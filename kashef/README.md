# كاشف النصوص — Kashef Text Search

المرحلة الأولى: الشخصية والتصميم فقط (الواجهة للعرض، البحث غير مفعّل).

- applicationId: `com.kashef.textsearch`
- البناء: GitHub Actions (`.github/workflows/build.yml`) — الناتج Artifact باسم `kashef-text-search-apk`

## أسرار GitHub (اختيارية، لا تُكتب في الكود)
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
عند غيابها يُوقَّع APK بمفتاح debug.

## إعلانات Start.io (مجهّزة ومعطّلة)
تتفعّل فقط عند: متغير المستودع `ADS_ENABLED=true` + سرّ `STARTIO_APP_ID`.
للتجربة فقط يمكن استعمال App ID التجريبي من توثيق Start.io، وإعلانات الاختبار تعمل تلقائيًا في debug.
