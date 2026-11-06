# التوقيع والإعلانات — خطوات الإعداد

لا يُكتب أي سرّ في الكود أو المستودع. كل شيء يُقرأ من GitHub Secrets/Variables.

## 1) توقيع الإصدار (نفس المفتاح في كل إصدار)
1. حوّل ملف المفتاح إلى base64 على جهازك (لا ترفع الملف نفسه):
   - Linux/macOS: `base64 -w0 my.keystore > keystore.b64`  (على macOS: `base64 -i my.keystore -o keystore.b64`)
2. في GitHub: Settings → Secrets and variables → Actions → New repository secret:
   - `KEYSTORE_BASE64` = محتوى keystore.b64
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
3. شغّل الـ workflow. مع الأسرار يُبنى `assembleRelease` موقّعًا بمفتاحك؛ وبدونها يُبنى debug.
4. خطوة «Show APK signing certificate» تطبع بصمة التوقيع وتقارنها بـ `EXPECTED_SHA256` في ملف الـ workflow (للإعلام فقط).
   إن لم تطابق، فالمفتاح المستخدم ليس المفتاح الذي تقصده؛ لا تنشر الإصدار قبل المراجعة.
5. لا تُنشئ مفتاحًا بديلًا: تغيير المفتاح يمنع تحديث النسخة المثبتة فوق القديمة.
6. ارفع `versionCode` (ويفضّل `versionName`) في `app/build.gradle.kts` عند كل إصدار تنشره.

## 2) إعلانات Start.io (معطّلة افتراضيًا)
1. أنشئ التطبيق في بوابة Start.io بالحزمة `com.kashef.textsearch` واحصل على App ID.
2. في GitHub:
   - Secret باسم `STARTIO_APP_ID` = معرّف التطبيق.
   - Variable (وليس Secret) باسم `ADS_ENABLED` = `true`.
3. أعد تشغيل الـ workflow. يظهر بانر واحد في الشاشة الرئيسية فقط، بعد جاهزية SDK.
4. نسخ debug تعرض **إعلانات اختبار** فقط (`setTestAdsEnabled`). لا تضغط على إعلانات حقيقية بنفسك.
5. موافقة الإعلانات المخصصة: خيار في تبويب «الحماية»، معطّل افتراضيًا، ويُرسل إلى Start.io بعلم `pas`.
6. لا تخترع معرّفات ولا تضعها في الكود.
