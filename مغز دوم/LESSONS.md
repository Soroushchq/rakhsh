# نکته‌برداری فنی — خطاها، راه‌حل‌ها و درس‌ها

## خطاهای کامپایل / CI و راه‌حل‌ها
1. **lambda و effectively-final (دو بار رخ داده!)**
   - `15feeb3d`: lambda متغیر `ok` را می‌گرفت که effectively final نبود → خطای کامپایل
   - `7b0243dd`: در `AmbergramProxyManager` متغیر `best` داخل حلقه دوباره مقداردهی می‌شد → با `removedFinal`/`bestFinal` (final محلی) حل شد
   - درس: هر جا lambda داخل حلقه متغیری را می‌گیرد که عوض می‌شود، بلافاصله نسخه `final` محلی بساز.
2. **مسیر artifact در CI**: برای flavor `afat` مسیر `apk/afat/debug` بود (`7e427bbb`)
3. **پکیج DownloadManager**: `android.app.DownloadManager` (نه android.provider) + import گمشده `TextUtils` (`d632b98e`)
4. **ContactsController**: نوع اکانت در SQL/URI باید literal باشد، نه متغیر (`789bfc4c`)
5. **quoting یادداشت انتشار در release.yml**: کاراکتر `"` داخل متن changelog پارس shell را می‌شکند و قبل از اجرای `gh` خطای glob می‌دهد (`e3aca0f2`) → هرگز quote دوتایی داخل رشته‌های changelog نگذار.
6. **رشته‌های فارسی**: placeholder خام `%` باعث کرش می‌شود → همیشه شماره‌دار بنویس: `%1$s`.

## احراز هویت گیت (نکته حیاتی)
- توکن GitHub در `/home/z/my-project/amber-git/git-credentials` نگه داشته می‌شود — این مسیر بین پیام‌ها پاک نمی‌شود؛ **مقدار توکن هرگز داخل ریپو نوشته نمی‌شود** (ریپو عمومی است)، فقط این مسیر ثبت می‌شود.
- بعد از هر ریست سندباکس کافی است: `bash /home/z/my-project/amber-git/restore-amber.sh` → احراز هویت + هویت گیت + کلون `/home/z/amber` همه یک‌جا بازگردانی می‌شوند.
- اگر کاربر توکن را عوض کرد: فقط محتوای `git-credentials` را به‌روز کن (فرمت: `https://USER:TOKEN@github.com`).

## درس‌های محیطی
- سندباکس ریست می‌شود: `/home/z/amber` و پوشه home پاک می‌شوند ولی `/home/z/my-project` پایدار است → اول وجود مسیرها را چک کن؛ بعد `bash /home/z/my-project/amber-git/restore-amber.sh` را اجرا کن؛ وضعیت واقعی ریپو را با GitHub API (کامیت‌ها/تگ‌ها/ریلیزها) کالیبره کن، به حافظه قدیمی اعتماد نکن.
- شبیه‌ساز اندروید در سندباکس وجود ندارد → «اسکرین‌شات از تم برنامه» ممکن نیست؛ جایگزین: بازبینی سیستماتیک کد کلیدهای رنگ تم (`Theme.java`، `DialogCell`).
- پوش معمولی به master امن است؛ CI فقط با تگ `v*` اجرا می‌شود.

## نکات دامنه پروژه
- نسخه در `gradle.properties` **ریشه ریپو** تعریف می‌شود (نه TMessagesProj).
- رشته‌ها همیشه دوزبانه: `values/strings.xml` (انگلیسی) + `values-fa/strings.xml` (فارسی).
- برندینگ: پک‌های زبان ریموت می‌توانند رشته‌ها را بازنویسی کنند → کلیدهای برند باید محافظت/بازنویسی runtime شوند (`d52ca265`).
- آیکون‌های جایگزین اندروید: چند `<activity-alias>` در AndroidManifest + `PackageManager.setComponentEnabledSetting` + انتخابگر در تنظیمات + رشته‌های `AppIcon*`.
- کلیدهای مهم تم: `Theme.java` (مثل `key_windowBackgroundWhiteBlackText`)؛ رنگ نام چت‌ها در `DialogCell`.
