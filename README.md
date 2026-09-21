# Ramesh Smart Shop \u2014 Complete Setup Guide

Yeh guide simple Hindi-English (Hinglish) mein hai taaki setup karna aasaan ho.

## 1. Project Structure (Kya kahan hai)

```
app/            -> Android Admin App (Kotlin + Jetpack Compose)
backend/        -> PHP + MySQL REST API
  config/       -> DB connection, CORS, auth helpers, Cloudinary helper
  api/          -> Sab API endpoints (admin, products, requests, purchases, settings, ai, upload, session)
  database.sql  -> Poora database schema, fresh import ke liye
  uploads/      -> Agar Cloudinary configure nahi hai, to product photos yahan save hoti hain
website/        -> User Shopping Website (single HTML file, mobile-first)
```

**IMPORTANT (Gradle Wrapper):** `gradle/wrapper/gradle-wrapper.jar` is fayl is ZIP mein
**shaamil nahi hai** \u2014 yeh ek binary file hai jo internet se download honi chahiye, aur
is sandbox mein mujhe internet access nahi tha. Iske bina `./gradlew` command line se
seedha kaam nahi karega. Fix karne ke 2 aasaan tareeke neeche "Android Build" section mein diye hain.

---

## 2. Backend Setup (PHP + MySQL)

### Step 1 \u2014 Files upload karo
`backend/` folder ko apne hosting (cPanel/XAMPP/any PHP+MySQL host) par upload karo.
PHP 8.0+ aur MySQL 5.7+/MariaDB chahiye. `curl` aur `gd` PHP extensions on hone chahiye
(Cloudinary aur image processing ke liye) \u2014 zyada tar hosting mein yeh already on hote hain.

### Step 2 \u2014 Database banao
phpMyAdmin (ya `mysql` CLI) khol kar `backend/database.sql` file ko **import** karo.
Yeh khud hi `ramesh_smart_shop` database bana dega, saari tables banayega, aur ek
DEVELOPMENT-ONLY admin daal dega:

```
Username: admin
Password: admin123
```

**Production mein jaane se pehle yeh password zaroor badlo** (Settings screen mein password
change ka option nahi hai abhi \u2014 phpMyAdmin mein admins table kholo, password column mein
naya bcrypt hash daalo, ya humse ek /admin/change-password endpoint bonus feature ke roop
mein maang sakte ho).

### Step 3 \u2014 Config file banao
`backend/config/config.example.php` ko copy karke `backend/config/config.php` banao, aur
apni real values daalo:

```php
'db_host'     => 'localhost',
'db_name'     => 'ramesh_smart_shop',
'db_user'     => 'your_mysql_user',
'db_password' => 'your_mysql_password',
'allowed_origin' => 'https://your-website-domain.com', // production mein '*' mat rakhna
'admin_token_ttl_hours' => 72,
```

`config.php` `.gitignore` mein hai \u2014 kabhi bhi is file ko GitHub par commit mat karna.

### Step 4 \u2014 Test karo
Browser mein kholo: `https://yourdomain.com/backend/api/settings/public.php`
Agar `{"success":true,...}` dikhe, backend chal raha hai.

---

## 3. Secrets Kahan Store Hote Hain (Bahut Important)

| Secret | Kahan store hota hai |
|---|---|
| MySQL password | `backend/config/config.php` (sirf server par) |
| AI (Gemini) API key | `settings` database table \u2014 Admin App ke Settings screen se save hota hai, sirf backend padhta hai |
| Cloudinary API secret | `settings` database table \u2014 wahi, backend-only |
| Admin login password | `admins` table mein bcrypt hash (kabhi plain text nahi) |

**Android app ya website JavaScript mein koi bhi secret hardcode NAHI hai.** App sirf apna
own backend URL jaanta hai; baaki sab backend khud sambhalta hai. Yehi wajah hai ki
`POST /api/ai/analyze-product.php` aur upload endpoint backend ke through jaate hain, seedha
Gemini/Cloudinary se nahi.

---

## 4. Gemini AI Setup

1. https://aistudio.google.com/apikey par jaake ek free Gemini API key banao.
2. Admin App kholo \u2192 Settings \u2192 AI section:
   - AI Provider: `gemini`
   - Model: `gemini-2.5-flash` (ya jo bhi model chaho)
   - AI API Key: apni key paste karo
3. SAVE SETTINGS dabao. Key seedha backend database mein save hoti hai, phone par nahi.
4. Ab jab bhi photo camera se lo, AI product ka naam/category/type/description suggest karega.
   Agar AI fail ho jaaye (galat JSON, network issue), app crash nahi hoga \u2014 fields khaali
   reh jayenge aur tum manually bhar sakte ho.

---

## 5. Cloudinary Setup (Optional par Recommended)

1. https://cloudinary.com par free account banao.
2. Dashboard se `Cloud Name`, `API Key`, `API Secret` copy karo.
3. Admin App \u2192 Settings \u2192 Cloudinary section mein teeno daalo, SAVE karo.
4. Ab jab bhi product photo upload hogi, wo Cloudinary par jaayegi (backend signed upload
   karta hai, secret kabhi app/website mein nahi jaata).
5. **Agar Cloudinary configure nahi karte**, koi problem nahi \u2014 photos automatically
   `backend/uploads/` folder mein local save ho jayengi. App khud detect karta hai.

---

## 6. Android Build

### Option A (Recommended) \u2014 Android Studio
1. Android Studio mein `File > Open`, is project ka root folder select karo.
2. Studio khud gradle wrapper detect karega. Agar `gradle-wrapper.jar` missing hone ki
   warning aaye, Studio usually khud fix kar deta hai jab tum "Sync Project with Gradle Files"
   dabaate ho (kyunki Studio apna khud ka bundled Gradle use karke wrapper jar bana/download
   kar sakta hai).
3. Agar phir bhi fix na ho: `File > Settings > Build Tools > Gradle` mein "Use Gradle from: 'wrapper'"
   ke bajaye apne installed Gradle version (matching `gradle-wrapper.properties` mein likhi
   version, abhi `9.3.1`) ko temporarily select karo, sync karo, phir wrapper wapas set kar do.
4. `app/src/main/java/.../data/SettingsManager.kt` mein default URL sirf emulator ke liye hai
   (`10.0.2.2`). Real phone par pehli baar app kholte hi Settings \u2192 Backend \u2192 API Base URL
   mein apna real backend URL daal ke Save + Test Connection karo.
5. Run karo (Shift+F10) ya `Build > Build Bundle(s)/APK(s) > Build APK(s)`.

### Option B \u2014 Command line
Agar tumhare paas Gradle already installed hai:
```
gradle wrapper --gradle-version 9.3.1
./gradlew assembleDebug
```
Yeh command khud missing `gradlew`/`gradle-wrapper.jar` ko sahi version ke saath bana degi.

### Debug keystore
Is ZIP mein `debug.keystore` already generated hai (standard Android debug credentials:
password `android`, alias `androiddebugkey`) taaki `assembleDebug` seedha chal sake. Yeh
sirf testing ke liye hai, Play Store release ke liye nahi.

### Release build (jab Play Store par daalna ho)
Apni khud ki release keystore banao (yeh kabhi share/commit mat karna):
```
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
Phir build karte waqt environment variables set karo:
```
KEYSTORE_PATH=/path/to/my-upload-key.jks STORE_PASSWORD=... KEY_PASSWORD=... ./gradlew assembleRelease
```

---

## 7. Android Settings Screen (No Rebuild Needed)

APK ek baar install karne ke baad, yeh sab kuch **Settings screen se** change ho sakta hai,
rebuild ki zaroorat nahi:

- **Backend**: API Base URL + Test Connection button
- **AI**: Provider, Model, Custom Endpoint, API Key
- **Cloudinary**: Cloud Name, API Key, API Secret
- **Shop**: Shop Name, Currency Symbol, Purchase History Duration (hours), Request Expiry
  Duration (minutes)

Secret fields (AI key, Cloudinary secret) khaali chhod do agar change nahi karna \u2014 purani
value backend mein safe rehti hai.

---

## 8. User Website Setup

1. `website/index.html` ko apne server par `backend/` folder ke **saath waale level** par
   rakho (jaise `public_html/website/index.html` aur `public_html/backend/...`), kyunki file
   ke andar API path relative hai: `const API_BASE = "../backend/api/";`
2. Agar website ek alag domain/subdomain par hai backend se, `index.html` khol kar us ek
   line ko poore URL se badal do, jaise: `const API_BASE = "https://api.yourdomain.com/api/";`
   \u2014 aur backend `config.php` mein `allowed_origin` ko us website domain par set karo (CORS).
3. Bas itna hi \u2014 koi build step nahi, yeh plain HTML+CSS+JS hai.

---

## 9. QR Code Setup

QR code sirf website ka URL hona chahiye, kuch aur nahi (no password/keys):
```
https://yourdomain.com/website/
```
Koi bhi free QR generator use karo (jaise `https://www.qr-code-generator.com/`), us URL ko
paste karke QR download karo aur print/counter par laga do. QR code mein koi secret nahi hai,
isliye safe hai print karke public jagah lagana.

---

## 10. Testing Checklist

- [ ] `backend/api/settings/public.php` browser mein khul raha hai (`success: true`)
- [ ] Admin App login ho raha hai (`admin` / `admin123`)
- [ ] Dashboard par 0/0/0 dikh raha hai (fresh install)
- [ ] Add Product \u2192 Camera se photo lo \u2192 AI analysis chal raha hai (ya gracefully fail ho raha hai)
- [ ] Product Name/Category/Price edit karke Save Product \u2014 product list mein dikhna chahiye
- [ ] Gallery se bhi photo select karke product add karo
- [ ] Products screen mein status toggle (Active/Inactive) kaam kar raha hai
- [ ] Website QR/URL kholo \u2014 Name+Gender bhar ke Continue Shopping
- [ ] Sirf ACTIVE products dikh rahe hain website par
- [ ] Search aur Category filter kaam kar rahe hain
- [ ] Cart mein quantity +/- theek se kaam kar raha hai
- [ ] SEND TO ADMIN \u2014 do baar jaldi jaldi tap karke dekho, duplicate request nahi banni chahiye
- [ ] Admin App \u2192 User Requests mein naya request dikh raha hai
- [ ] COMPLETE PURCHASE \u2192 confirmation dialog \u2192 confirm karne ke baad Purchase History mein dikhe
- [ ] Ek request ko Cancel karke dekho
- [ ] Product ka price change karo \u2014 purane pending/completed requests ka total change NAHI hona chahiye
- [ ] Settings \u2192 Test Connection sahi/galat URL ke saath try karo
- [ ] Internet band karke app/website try karo \u2014 error message aana chahiye, crash nahi

---

## 11. Troubleshooting

| Problem | Solution |
|---|---|
| "Database connection error" | `backend/config/config.php` ki DB details check karo |
| Login fail ho raha hai | `admins` table mein username/password hash check karo; default `admin`/`admin123` |
| AI analysis "not configured" bolta hai | Settings \u2192 AI \u2192 API Key save karo |
| Photos upload nahi ho rahi | PHP `upload_max_filesize` aur `post_max_size` (php.ini) kam se kam 10M rakho |
| Website par products nahi dikh rahe | Product ka status ACTIVE hai ya nahi check karo; CORS/`allowed_origin` check karo |
| CORS error browser console mein | `config.php` mein `allowed_origin` ko website ke exact domain se match karo |
| Gradle build fail | Section 6 dekho \u2014 gradle wrapper jar is ZIP mein nahi hai |

---

## 12. Known Limitations (ईमानदारी से)

- Gradle wrapper jar (binary file) is package mein nahi hai \u2014 Section 6 mein fix diya hai.
- Password change ke liye abhi koi UI nahi hai (phpMyAdmin se manually karna hoga).
- 24-hour cleanup ek real cron job nahi hai \u2014 yeh tabhi chalta hai jab koi relevant API call
  (`purchases`, `requests`, `dashboard`) hit hoti hai. Agar app kabhi use na ho, purane records
  tab tak rahenge jab tak agli baar koi request nahi aati. Real cron ke liye apne hosting
  panel mein ek cron job add kar sakte ho jo `backend/api/purchases/index.php` ko har ghante
  hit kare (GET request, admin token ke saath).
- AI sirf Gemini support karta hai abhi (jaisa spec mein bola gaya tha, "modular rakho taaki
  baad mein aur provider add ho sakein" \u2014 architecture is layout se allow karta hai).
