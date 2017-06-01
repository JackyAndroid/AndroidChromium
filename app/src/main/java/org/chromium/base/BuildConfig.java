package org.chromium.base;
public class BuildConfig {
    public static boolean isMultidexEnabled() {
        return false;
    }
    public static final boolean DCHECK_IS_ON = false;
    public static final String[] COMPRESSED_LOCALES = {};
      //{"am","ar","bg","ca","cs","da","de","el","en-GB","en-US","es","es-419","fa","fi","fr",
      //      "he","hi","hr","hu","id","it","ja","ko","lt","lv","nb","nl","pl","pt-BR","pt-PT",
      //        "ro","ru","sk","sl","sr","sv","sw","th","tr","uk","vi","zh-CN","zh-TW"};
    public static final String[] UNCOMPRESSED_LOCALES =
              {"en-US","zh-CN"};
}
