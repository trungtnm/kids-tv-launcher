package com.kidstv.launcher

import java.util.Locale

/** Every text the app shows on the TV or sends to the web page, in Vietnamese and English. */
enum class S(private val vi: String, private val en: String) {
    KID("Bé", "Kid"),
    LITTLE_ONE("Bé nhỏ", "Little one"),
    BIG_KID("Bé lớn", "Big kid"),

    PAUSED("Ba mẹ đã tạm dừng TV", "TV paused by your parents"),
    BEDTIME("Đến giờ đi ngủ rồi", "It's bedtime"),
    QUOTA("Hôm nay con đã xem đủ giờ rồi", "That's all the TV time for today"),
    NO_PROFILE("Chưa có hồ sơ nào", "No profile yet"),
    APP_LOCKED("Ứng dụng này cần ba mẹ mở khóa", "This app needs a parent to unlock it"),

    SCAN_QR("Quét mã để quản lý trên điện thoại", "Scan to manage from your phone"),
    NO_NETWORK("TV chưa kết nối mạng", "The TV is not connected to a network"),
    GUARD_OFF("⚠ Dịch vụ khóa chưa bật", "⚠ Lock service is off"),
    LOCK_AGAIN("Khóa lại", "Lock again"),
    MINUTES_LEFT("Còn %d phút hôm nay", "%d min left today"),
    PARENTS("Ba mẹ", "Parents"),
    SETTINGS("Cài đặt", "Settings"),
    NO_APPS("Chưa có ứng dụng nào được phép. Ba mẹ thêm trên điện thoại nhé.", "No apps allowed yet. Parents can add some from their phone."),
    PARENT_UNLOCK("Ba mẹ mở khóa", "Parent unlock"),
    WHO_IS_WATCHING("Ai đang xem?", "Who's watching?"),

    PIN_CREATE("Tạo mã PIN cho ba mẹ (4–8 số)", "Create a parent PIN (4–8 digits)"),
    PIN_REPEAT("Nhập lại mã PIN", "Enter the PIN again"),
    PIN_FORMAT("Mã PIN cần 4 đến 8 chữ số", "The PIN must be 4 to 8 digits"),
    PIN_MISMATCH("Hai lần nhập không khớp, hãy tạo lại", "The two PINs don't match, start again"),
    PIN_ENTER("Nhập mã PIN của ba mẹ", "Enter the parent PIN"),
    PIN_LOCKOUT("Sai quá nhiều lần, thử lại sau %d giây", "Too many wrong attempts, try again in %d seconds"),
    PIN_WRONG("Sai mã PIN", "Wrong PIN"),

    HINT_BEDTIME("Chúc con ngủ ngon. Mai mình xem tiếp nhé!", "Sleep tight. We'll watch more tomorrow!"),
    HINT_QUOTA("Mình nghỉ mắt và chơi trò khác nhé!", "Let's rest our eyes and play something else!"),
    HINT_PAUSED("Chờ ba mẹ một chút nhé.", "Hang on a moment for your parents."),
    HINT_APP("Con nhờ ba mẹ mở giúp nhé.", "Ask a parent to open it for you."),

    GO_HOME("Về màn hình chính", "Go to home screen"),
    WHAT_NOW("Ba mẹ muốn làm gì?", "What would you like to do?"),
    RESUME("Cho xem tiếp", "Resume"),
    ADD_MINUTES("Thêm %d phút", "Add %d min"),
    PARENT_MODE("Chế độ ba mẹ", "Parent mode"),

    WEB_GUARD_OFF("Dịch vụ khóa trên TV chưa chạy", "The lock service on the TV is not running"),
    WEB_BAD_YOUTUBE("Link YouTube không hợp lệ", "That is not a valid YouTube link"),
    WEB_YOUTUBE_LOCKED("TV đang khóa YouTube: %s", "YouTube is locked on the TV: %s"),
    WEB_YOUTUBE_FAILED("Không mở được YouTube trên TV", "Couldn't open YouTube on the TV"),
    WEB_APP_LOCKED("TV đang khóa ứng dụng này: %s", "This app is locked on the TV: %s"),
    WEB_APP_FAILED("Không mở được ứng dụng", "Couldn't open the app"),
    WEB_PIN_CURRENT_WRONG("Mã PIN hiện tại không đúng", "The current PIN is wrong"),
    WEB_PIN_FORMAT("Mã PIN mới cần 4 đến 8 chữ số", "The new PIN must be 4 to 8 digits");

    /** The text in the language chosen by the parents, with [args] filled in. */
    fun text(vararg args: Any): String =
        String.format(Locale.US, if (Store.lang.value == "en") en else vi, *args)
}
