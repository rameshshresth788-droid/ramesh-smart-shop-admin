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

## 6. Android Build \u2014 GitHub Actions (Primary Method)

Is project mein `.github/workflows/build-apk.yml` already hai. Koi bhi local Gradle/Android
Studio install kiye bina, GitHub khud APK bana dega.

### Step-by-step (bilkul exact)

1. **Naya GitHub repository banao**
   - github.com par login karo \u2192 top-right `+` \u2192 "New repository"
   - Naam do (jaise `ramesh-smart-shop-admin`) \u2192 Private ya Public, jo chaho \u2192 **Create repository**
   - README/gitignore add mat karo yahan (hamara project khud se sab laata hai)

2. **Project upload karo**
   - Is poore ZIP ko extract karo apne computer par
   - Terminal/cmd mein extract ki hui folder ke andar jaake:
     ```
     git init
     git add .
     git commit -m "Initial commit - Ramesh Smart Shop Admin"
     git branch -M main
     git remote add origin https://github.com/<your-username>/<your-repo>.git
     git push -u origin main
     ```
   - (GitHub Desktop use karna hai to: "Add local repository" \u2192 folder select \u2192 "Publish repository")

3. **Actions enable karo (agar zaroorat pade)**
   - Repo ke andar **Actions** tab par click karo
   - Naye repo mein yeh usually already ON hota hai; agar koi "I understand my workflows, go ahead and enable them" button dikhe, use dabao

4. **Workflow run karo**
   - **Actions** tab \u2192 left side "Build Debug APK" workflow select karo
   - **Run workflow** button (top-right, dropdown) \u2192 branch `main` select \u2192 **Run workflow**
   - (Agar tumne push kiya hai `main` par, yeh apne aap bhi chal jaayega \u2014 manual trigger ki zaroorat nahi)

5. **Build ka status dekho**
   - Us workflow run par click karo, jobs/steps live dikhenge (checkout \u2192 JDK \u2192 SDK \u2192 wrapper \u2192 build \u2192 upload)
   - Poora build usually 5-10 minute leta hai pehli baar (SDK download ke wajah se)

6. **APK download karo**
   - Jab run "green tick" ho jaaye, us run page ko kholo
   - Neeche scroll karo **Artifacts** section tak
   - `ramesh-smart-shop-admin-debug-apk` par click karo \u2014 yeh ek `.zip` download karega jiske andar `app-debug.apk` hoga
   - Us APK ko phone mein transfer karke install karo (Unknown Sources allow karna padega)

7. **Pehli baar app kholte hi**
   - Settings \u2192 Backend \u2192 API Base URL mein apna real hosted backend URL daalo (Section 2 dekho)
   - Login: `admin` / `admin123` (Section 2 ka default)

### Workflow kya karta hai (transparency ke liye)
`gradle/wrapper/gradle-wrapper.jar` jo is ZIP mein hai, wo ek **placeholder hai, real working
binary nahi** \u2014 yeh binary file internet se download honi chahiye, aur jis sandboxed
environment mein maine yeh project taiyaar kiya, wahan internet access nahi tha, isliye main
khud ek genuinely valid jar banake nahi de saka. Isko chhupaya nahi gaya \u2014 workflow file khud
ismein ek step rakhta hai jo GitHub ke runner par (jahan internet hota hai) seedha
`services.gradle.org` se asli Gradle 9.3.1 download karta hai, sirf **ek baar `gradle wrapper`
command chalane ke liye**, jo `gradlew`, `gradlew.bat`, aur `gradle-wrapper.jar` ko sahi,
matching version ke saath dobara bana deta hai. Uske baad har build step sirf `./gradlew` hi
use karta hai \u2014 bilkul waise hi jaise ek normal contributor apne machine par karta.
Iska matlab: **workflow chalne ke baad tumhare paas ek genuinely valid `gradle-wrapper.jar`
bhi ban jaata hai** agar tum us commit ko wapas apne repo mein le aana chaho.

### Agar workflow fail ho jaaye
- **Actions** tab \u2192 failed run \u2192 jo step laal (red) hai use expand karo, poora error message
  padho (usually SDK package naam ya Gradle download URL se related hota hai)
- Sabse common cause: naya AGP/Gradle version release hone se `distributionUrl` ya
  `build-tools;36.0.0` jaisi values outdated ho jaayein \u2014 tab `gradle/wrapper/gradle-wrapper.properties`
  aur `.github/workflows/build-apk.yml` dono mein version numbers update karne padenge

---

## 6b. Android Build \u2014 Local (Optional, Not Required)

Agar kabhi local build bhi karna ho:

**Android Studio**: `File > Open` \u2192 project folder select \u2192 Studio khud missing wrapper jar
fix kar dega "Sync Project with Gradle Files" par.

**Command line** (agar Gradle installed hai):
```
gradle wrapper --gradle-version 9.3.1
./gradlew assembleDebug
```

### Debug keystore
Is ZIP mein `debug.keystore` already generated hai (standard Android debug credentials:
password `android`, alias `androiddebugkey`) taaki `assembleDebug` seedha chal sake. Yeh
sirf testing ke liye hai, Play Store release ke liye nahi.

### Release build (jab Play Store par daalna ho)
Apni khud ki release keystore banao (yeh kabhi share/commit mat karna, GitHub secrets mein
bhi tabhi daalna jab genuinely release banani ho):
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
| Gradle build fail (local) | Section 6b dekho \u2014 committed gradle wrapper jar sirf placeholder hai, real build GitHub Actions se karo (Section 6) |
| GitHub Actions workflow fail | Section 6 ka "Agar workflow fail ho jaaye" part dekho |

---

## 12. Known Limitations (ईमानदारी से)

- `gradle/wrapper/gradle-wrapper.jar` jo commit kiya gaya hai wo ek placeholder hai, real
  working binary nahi (internet-less sandbox mein bana). GitHub Actions workflow (Section 6)
  isse khud fix kar leta hai runtime par \u2014 koi manual step nahi chahiye.
- Password change ke liye abhi koi UI nahi hai (phpMyAdmin se manually karna hoga).
- 24-hour cleanup ek real cron job nahi hai \u2014 yeh tabhi chalta hai jab koi relevant API call
  (`purchases`, `requests`, `dashboard`) hit hoti hai. Agar app kabhi use na ho, purane records
  tab tak rahenge jab tak agli baar koi request nahi aati. Real cron ke liye apne hosting
  panel mein ek cron job add kar sakte ho jo `backend/api/purchases/index.php` ko har ghante
  hit kare (GET request, admin token ke saath).
- AI sirf Gemini support karta hai abhi (jaisa spec mein bola gaya tha, "modular rakho taaki
  baad mein aur provider add ho sakein" \u2014 architecture is layout se allow karta hai).
