# 외부 의존성 설정
이 문서에서는 로드캡처 개발 환경 설정을 위한 외부 의존성을 설정하는 방법에 대해 다룹니다. 외부 의존성을 제거하여 간단한 사용자 정의 대체 구현체를 사용하거나 외부 의존성을 유지하여 전체 서비스의 동작을 확인할 수 있습니다. 

## 1. 외부 의존성 제거

### app/build.gradle

```groovy
plugins {
    id 'com.android.application'
    // id 'com.google.android.libraries.mapsplatform.secrets-gradle-plugin' // Maps API 키 관련
    // id 'com.google.gms.google-services' // Firebase 관련
    id 'dagger.hilt.android.plugin'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.kapt'
    id 'org.jetbrains.kotlin.plugin.parcelize'
    id 'androidx.navigation.safeargs.kotlin'
}

// local.properties 로딩 부분 제거 또는 주석 처리
// def Properties properties = new Properties()
// properties.load(project.rootProject.file('local.properties').newDataInputStream())

android {
    // ...
    
    defaultConfig {
        // ...
        // 키 관련 buildConfigField 설정 제거 (필요한 경우)
    }
    
    signingConfigs {
        // 서명 설정 주석 처리
        // release {
        //     storeFile file("C:\\Users\\leewo\\roadcapture.jks")
        //     storePassword properties.getProperty("STORE_PASSWORD")
        //     keyAlias properties.getProperty("KEY_ALIAS")
        //     keyPassword properties.getProperty("KEY_PASSOWORD")
        // }
    }
    
    // ...
}

dependencies {
    // local.properties 관련 모든 의존성 제거
    
    // 1. 소셜 로그인 관련
    // implementation libs.oauth // 네이버
    // implementation libs.v2.all // 카카오
    // implementation libs.v2.user
    // implementation libs.googleid // 구글
    // implementation libs.facebook.android.sdk // 페이스북
    // implementation libs.gms.play.services.auth // Google 인증
    
    // 2. Firebase 관련
    // implementation platform(libs.firebase.bom)
    // implementation(libs.firebase.firestore)
    // implementation(libs.firebase.functions.ktx)
    // implementation(libs.firebase.storage)
    // implementation(libs.firebase.auth)
    // implementation(libs.firebase.analytics)
    
    // 3. 지도 및 위치 관련
    // implementation libs.play.services.location // LocationIQ
    // implementation libs.play.services.maps // Google Maps
    // implementation libs.places
    // implementation libs.android.maps.utils
    
    // 4. Algolia 검색 관련
    // implementation libs.algoliasearch.android
    
    // 5. 광고 관련
    // implementation libs.play.services.ads
    // implementation libs.play.services.ads.identifier
    
    // 6. Branch.io 관련
    // implementation libs.branch
    
    // 나머지 의존성 유지...
}
```

### build.gradle (Project)
```groovy
buildscript {
    dependencies {
        classpath libs.dagger.hilt.android.gradle.plugin
        classpath libs.androidx.navigation.safe.args.gradle.plugin
        // classpath libs.secrets.gradle.plugin // 시크릿 플러그인 제거
        classpath libs.kotlin.serialization
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    // alias(libs.plugins.mapsplatform.secrets.gradle.plugin) apply false // Maps 시크릿 플러그인 제거
    alias(libs.plugins.navigation.safeargs.kotlin) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    // alias(libs.plugins.google.services) apply false // Firebase 플러그인 제거
}
```

### settings.gradle
```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Firebase, Maps 등 특정 저장소 제거 (필요한 경우)
    }
}
```

## 2. 외부 의존성 유지

### Firebase 설정

Firebase는 앱의 인증, 데이터베이스, 저장소, 함수 및 호스팅 기능을 제공합니다. 다음 단계에 따라 Firebase를 설정합니다.

#### Firebase 프로젝트 생성
- [Firebase 콘솔](https://console.firebase.google.com/)에 접속
- "프로젝트 추가" 클릭 후 프로젝트 이름 입력 (예: "roadcapture")
- Google Analytics 사용 설정 (권장)
- "프로젝트 만들기" 클릭

#### Android 앱 등록
- 프로젝트 개요 페이지에서 Android 아이콘 클릭
- 패키지 이름 입력: `com.tenacy.roadcapture`
- SHA-1 인증서 지문 추가 (개발 및 릴리스용)
- `google-services.json` 파일 다운로드 후 `app/` 디렉토리에 추가

#### Authentication 설정
- Google (클라이언트 ID 및 보안 비밀번호 구성)
- Facebook (앱 ID 및 앱 시크릿 구성)
- OpenID Connect (카카오 로그인용)
  - 카카오 앱 OpenID Connect 활성화 
  - 리다이렉트 URI: https://your-firebase-app/__/auth/handler
  - 클라이언트 ID: 카카오 앱 네이티브 앱 키
  - 발급자 URL: https://kauth.kakao.com
  - 클라이언트 보안 비밀번호: 카카오 앱 클라이언트 시크릿 

#### Cloud Firestore 설정

#### Storage 설정
- `/images/default_profile.jpg` 경로에 기본 프로필 이미지 파일 업로드

#### Cloud Functions 설정
- Node.js 및 Firebase CLI 설치
- 함수 구현
    - `delete-album.js`: 앨범 및 관련 문서(추억, 위치, 스크랩, 신고)를 트랜잭션으로 삭제하고 Storage에서 이미지 파일도 삭제
    - `delete-user-data-http.js`: HTTP 요청으로 사용자의 모든 데이터 삭제
    - `auth-triggers.js`: Firebase Auth에서 사용자 삭제 시 Storage 파일 정리
    - `reset-daily-credits.js`: 사용자 타임존별 자정 시점에 일일 크레딧 초기화
    - `verify-subscription.js`: Google Play 구독 상태 확인
    - `check-user-exists.js`: Express 앱으로 구현된 웹 API 및 공유 페이지 서비스

#### Extensions 설정 (Algolia 검색)
- "Algolia 검색" 확장 선택 및 설치
- Algolia API 키 및 앱 ID 구성
- 인덱싱할 컬렉션 및 필드 설정
  - albums
    - title
    - userRef
    - userDisplayName
    - memoryAddressTags
    - memoryPlaceNames
    - createdAt
    - isPublic
  - scraps
    - userRef
    - albumTitle
    - albumUserDisplayName
    - albumMemoryAddressTags
    - albumMemoryPlaceNames
    - albumPublic
    - createdAt
    
#### Hosting 설정 (공유 링크용)
- 공유 페이지 템플릿 생성 및 배포
- OG 메타 태그 설정 (공유 시 미리보기용)

### local.properties
```
sdk.dir=/path/to/your/Android/Sdk

NAVER_CLIENT_ID="your_naver_client_id"
NAVER_CLIENT_SECRET="your_naver_client_secret"
NAVER_CLIENT_NAME="your_naver_client_name"
NAVER_AUTH_URL="your_naver_auth_url"
NAVER_AUTH_CALLBACK_URI="your_naver_auth_callback_uri"
KAKAO_CLIENT_ID="your_kakao_client_id"
GOOGLE_CLIENT_ID="your_google_client_id"
GOOGLE_CLIENT_SECRET="your_google_client_secret"
FACEBOOK_APP_ID="your_facebook_app_id"
FB_LOGIN_PROTOCOL_SCHEME="your_fb_login_protocol_scheme"
FACEBOOK_SECRET_CODE="your_facebook_secret_code"
FACEBOOK_CLIENT_TOKEN="your_facebook_client_token"

STORAGE_BASE_URL="your_firebase_storage_url"
FUNCTIONS_BASE_URL="your_firebase_functions_url"
WEB_HOST="your_firebase_web_host"
WEB_BASE_URL="your_firebase_web_base_url"

LOCATION_IQ_ACCESS_TOKEN="your_location_iq_access_token"

MAPS_API_KEY="your_google_maps_api_key"

ALGOLIA_APP_ID="your_algolia_app_id"
ALGOLIA_API_KEY="your_algolia_api_key"

AD_MOB_APP_ID="your_admob_app_id"
AD_MOB_APP_SAVE_MEMORY_ID="your_admob_save_memory_id"
AD_MOB_APP_HOME_ALBUM_ID="your_admob_home_album_id"
AD_MOB_APP_UNIT_REWARD_TEST_ID="your_admob_reward_test_id"
AD_MOB_APP_UNIT_NATIVE_TEST_ID="your_admob_native_test_id"

STORE_PASSWORD="your_store_password"
KEY_ALIAS="your_key_alias"
KEY_PASSOWORD="your_key_password"

BRANCH_LIVE_KEY="your_branch_live_key"
BRANCH_LIVE_SECRET="your_branch_live_secret"
BRANCH_TEST_KEY="your_branch_test_key"
BRANCH_TEST_SECRET="your_branch_test_secret"
BRANCH_LIVE_DEFAULT_LINK_DOMAIN="your_branch_live_default_link_domain"
BRANCH_LIVE_ALTERNATIVE_LINK_DOMAIN="your_branch_live_alternative_link_domain"
BRANCH_TEST_DEFAULT_LINK_DOMAIN="your_branch_test_default_link_domain"
BRANCH_TEST_ALTERNATIVE_LINK_DOMAIN="your_branch_test_alternative_link_domain"
BRANCH_APP_ID="your_branch_app_id"

DEVELOPMENT="false"
```