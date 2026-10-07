#include <jni.h>
#include <android/bitmap.h>
#include <libraw/libraw.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>

namespace {
struct Cancellation {
    JNIEnv *env;
    jobject token;
    jfieldID cancelled;
    bool requested() const { return env->GetBooleanField(token, cancelled) == JNI_TRUE; }
};
int progress(void *context, enum LibRaw_progress, int, int) {
    return static_cast<Cancellation *>(context)->requested() ? 1 : 0;
}
void check(int code, const char *phase) {
    if (code != LIBRAW_SUCCESS)
        throw std::runtime_error(std::string(phase) + ": " + libraw_strerror(code));
}
void throwJava(JNIEnv *env, const char *type, const std::string &message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass(type), message.c_str());
}
struct PixelsLock {
    JNIEnv *env;
    jobject bitmap;
    ~PixelsLock() { AndroidBitmap_unlockPixels(env, bitmap); }
};
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_shahgul_rawphotoconverter_conversion_NativeRawDecoder_decode(
    JNIEnv *env, jobject, jstring path, jint maxEdge, jobject token) {
    Cancellation cancellation{env, token, env->GetFieldID(env->GetObjectClass(token), "cancelled", "Z")};
    if (!cancellation.cancelled || env->ExceptionCheck()) return nullptr;
    try {
        if (cancellation.requested()) {
            throwJava(env, "java/util/concurrent/CancellationException", "Conversion cancelled");
            return nullptr;
        }
        if (maxEdge != 0 && (maxEdge < 256 || maxEdge > 8000)) throw std::runtime_error("Invalid output dimensions");
        const char *chars = env->GetStringUTFChars(path, nullptr);
        if (!chars) return nullptr;
        std::string filename(chars);
        env->ReleaseStringUTFChars(path, chars);
        LibRaw raw;
        raw.imgdata.rawparams.max_raw_memory_mb = 384;
        raw.set_progress_handler(progress, &cancellation);
        check(raw.open_file(filename.c_str()), "Open RAW");
        const auto &sizes = raw.imgdata.sizes;
        if (static_cast<uint64_t>(sizes.raw_width) * sizes.raw_height > 50000000ULL)
            throw std::runtime_error("This RAW exceeds the alpha's 50 MP memory limit");
        auto &params = raw.imgdata.params;
        params.output_color = 1; // sRGB, using the camera colour matrix.
        params.output_bps = 8;
        params.use_camera_wb = 1;
        params.user_qual = 3; // AHD demosaic.
        params.user_flip = -1; // Physically apply the camera's orientation.
        // Gamma matching sRGB's power and linear toe, rather than dcraw's default.
        params.gamm[0] = 1.0 / 2.4;
        params.gamm[1] = 12.92;
        params.no_auto_bright = 1;
        check(raw.unpack(), "Unpack RAW");
        check(raw.dcraw_process(), "Develop RAW");
        int error = 0;
        std::unique_ptr<libraw_processed_image_t, decltype(&LibRaw::dcraw_clear_mem)> image(
            raw.dcraw_make_mem_image(&error), &LibRaw::dcraw_clear_mem);
        check(error, "Render RAW");
        if (!image || image->type != LIBRAW_IMAGE_BITMAP || image->colors != 3 || image->bits != 8 ||
            image->width == 0 || image->height == 0 ||
            image->data_size < static_cast<uint64_t>(image->width) * image->height * 3)
            throw std::runtime_error("RAW decoder returned unsupported pixel data");
        raw.recycle(); // Release sensor/demosaic allocations before the Android bitmap.
        const double scale = maxEdge == 0 ? 1.0 : std::min(1.0, static_cast<double>(maxEdge) / std::max(image->width, image->height));
        const int width = std::max(1, static_cast<int>(std::lround(image->width * scale)));
        const int height = std::max(1, static_cast<int>(std::lround(image->height * scale)));
        jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
        jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
        jobject config = env->GetStaticObjectField(configClass, env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;"));
        jobject bitmap = env->CallStaticObjectMethod(bitmapClass, env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"), width, height, config);
        if (env->ExceptionCheck() || !bitmap) return nullptr;
        void *pixels = nullptr;
        AndroidBitmapInfo info{};
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
            AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
            throw std::runtime_error("Unable to allocate output pixels");
        PixelsLock lock{env, bitmap};
        for (int y = 0; y < height; ++y) {
            if (cancellation.requested()) {
                throwJava(env, "java/util/concurrent/CancellationException", "Conversion cancelled");
                return nullptr;
            }
            auto *row = static_cast<uint8_t *>(pixels) + y * info.stride;
            const double sy = std::clamp((y + 0.5) * image->height / height - 0.5, 0.0, static_cast<double>(image->height - 1));
            const int y0 = static_cast<int>(sy), y1 = std::min(y0 + 1, image->height - 1);
            const double fy = sy - y0;
            for (int x = 0; x < width; ++x) {
                const double sx = std::clamp((x + 0.5) * image->width / width - 0.5, 0.0, static_cast<double>(image->width - 1));
                const int x0 = static_cast<int>(sx), x1 = std::min(x0 + 1, image->width - 1);
                const double fx = sx - x0;
                for (int c = 0; c < 3; ++c) {
                    const auto sample = [&](int px, int py) { return image->data[(py * image->width + px) * 3 + c]; };
                    const double a = sample(x0, y0) * (1 - fx) + sample(x1, y0) * fx;
                    const double b = sample(x0, y1) * (1 - fx) + sample(x1, y1) * fx;
                    row[x * 4 + c] = static_cast<uint8_t>(std::lround(a * (1 - fy) + b * fy));
                }
                row[x * 4 + 3] = 255;
            }
        }
        return bitmap;
    } catch (const std::exception &error) {
        throwJava(env, cancellation.requested() ? "java/util/concurrent/CancellationException" : "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throwJava(env, "java/lang/IllegalStateException", "Native RAW development failed");
    }
    return nullptr;
}

// Extract the camera JPEG without demosaicing or altering its compressed pixels.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_shahgul_rawphotoconverter_conversion_NativeRawDecoder_cameraPreview(
    JNIEnv *env, jobject, jstring path, jobject token) {
    Cancellation cancellation{env, token, env->GetFieldID(env->GetObjectClass(token), "cancelled", "Z")};
    if (!cancellation.cancelled || env->ExceptionCheck()) return nullptr;
    try {
        if (cancellation.requested()) throw std::runtime_error("Conversion cancelled");
        const char *chars = env->GetStringUTFChars(path, nullptr);
        if (!chars) return nullptr;
        std::string filename(chars);
        env->ReleaseStringUTFChars(path, chars);
        LibRaw raw;
        raw.imgdata.rawparams.max_raw_memory_mb = 384;
        raw.set_progress_handler(progress, &cancellation);
        check(raw.open_file(filename.c_str()), "Open RAW");
        check(raw.unpack_thumb(), "Camera preview unavailable; choose Develop RAW");
        const auto &thumb = raw.imgdata.thumbnail;
        if (thumb.tformat != LIBRAW_THUMBNAIL_JPEG || !thumb.thumb ||
            thumb.tlength == 0 || thumb.tlength > 32U * 1024 * 1024)
            throw std::runtime_error("No supported embedded camera JPEG. Choose Develop RAW.");
        if (cancellation.requested()) throw std::runtime_error("Conversion cancelled");
        jbyteArray result = env->NewByteArray(static_cast<jsize>(thumb.tlength));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(thumb.tlength), reinterpret_cast<const jbyte *>(thumb.thumb));
        return result;
    } catch (const std::exception &error) {
        throwJava(env, cancellation.requested() ? "java/util/concurrent/CancellationException" : "java/lang/IllegalStateException", error.what());
    } catch (...) { throwJava(env, "java/lang/IllegalStateException", "Camera preview extraction failed"); }
    return nullptr;
}
