#include <string>

extern "C" const char* meme_native_runtime_version() {
    static const std::string version = "1";
    return version.c_str();
}
