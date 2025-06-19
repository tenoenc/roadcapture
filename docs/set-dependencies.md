# External Dependencies Setup

## Overview

RoadCapture는 다양한 외부 서비스(Firebase, Google Maps, Social Login 등)와 긴밀하게 통합되어 있습니다. 이 문서는 프로젝트 빌드 및 실행을 위해 필요한 외부 의존성 설정 방법을 다룹니다.

개발 목적에 따라 두 가지 모드로 환경을 구성할 수 있습니다.
1. **Full Configuration**: 모든 기능이 정상 동작하는 표준 개발 환경
2. **Minimal Configuration**: 외부 API 키 없이 UI 및 로컬 로직만 확인하기 위한 제한적 환경

## Option 1: Full Configuration (Recommended)

서비스의 모든 기능을 사용하기 위해 필요한 API 키와 클라우드 리소스 설정 방법입니다.

### 1. Configure local.properties

프로젝트 루트 디렉토리의 `local.properties` 파일에 다음 키 값들을 정의해야 합니다. 보안을 위해 이 파일은 Git에 포함되지 않아야 합니다.

```properties
# Android SDK
sdk.dir=/path/to/your/Android/Sdk

# ---------------------------------------------------------
# Authentication (Social Login)
# ---------------------------------------------------------
# Naver
NAVER_CLIENT_ID="your_naver_client_id"
NAVER_CLIENT_SECRET="your_naver_client_secret"
NAVER_CLIENT_NAME="RoadCapture"
NAVER_AUTH_URL="[https://nid.naver.com/oauth2.0/authorize](https://nid.naver.com/oauth2.0/authorize)"
NAVER_AUTH_CALLBACK_URI="your_app_scheme://callback"

# Kakao
KAKAO_CLIENT_ID="your_kakao_native_app_key"

# Google & Facebook
GOOGLE_CLIENT_ID="your_web_client_id.apps.googleusercontent.com"
GOOGLE_CLIENT_SECRET="your_google_client_secret"
FACEBOOK_APP_ID="your_facebook_app_id"
FACEBOOK_SECRET_CODE="your_facebook_app_secret"
FACEBOOK_CLIENT_TOKEN="your_facebook_client_token"
FB_LOGIN_PROTOCOL_SCHEME="fb[your_app_id]"

# ---------------------------------------------------------
# Firebase & Cloud Services
# ---------------------------------------------------------
STORAGE_BASE_URL="gs://your-project-id.firebasestorage.app"
FUNCTIONS_BASE_URL="[https://your-region-your-project-id.cloudfunctions.net](https://your-region-your-project-id.cloudfunctions.net)"
WEB_HOST="your-project-id.web.app"
WEB_BASE_URL="[https://your-project-id.web.app](https://your-project-id.web.app)"

# ---------------------------------------------------------
# Maps & Location
# ---------------------------------------------------------
MAPS_API_KEY="your_google_maps_api_key"
LOCATION_IQ_ACCESS_TOKEN="your_locationiq_token"

# ---------------------------------------------------------
# Search (Algolia)
# ---------------------------------------------------------
ALGOLIA_APP_ID="your_algolia_app_id"
ALGOLIA_API_KEY="your_algolia_search_api_key"

# ---------------------------------------------------------
# Monetization & Marketing
# ---------------------------------------------------------
# AdMob (Use test IDs for development)
AD_MOB_APP_ID="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"
AD_MOB_APP_SAVE_MEMORY_ID="ca-app-pub-..."
AD_MOB_APP_HOME_ALBUM_ID="ca-app-pub-..."
AD_MOB_APP_UNIT_REWARD_TEST_ID="ca-app-pub-3940256099942544/5224354917"
AD_MOB_APP_UNIT_NATIVE_TEST_ID="ca-app-pub-3940256099942544/2247696110"

# Branch.io
BRANCH_APP_ID="your_branch_app_id"
BRANCH_LIVE_KEY="key_live_..."
BRANCH_LIVE_SECRET="secret_live_..."
BRANCH_TEST_KEY="key_test_..."
BRANCH_TEST_SECRET="secret_test_..."

# ---------------------------------------------------------
# Signing Config (Release)
# ---------------------------------------------------------
STORE_PASSWORD="your_store_password"
KEY_ALIAS="your_key_alias"
KEY_PASSOWORD="your_key_password"

# ---------------------------------------------------------
# Flags
# ---------------------------------------------------------
DEVELOPMENT="true"

```

### 2. Firebase Setup

[Firebase Console](https://console.firebase.google.com/)에서 프로젝트를 생성하고 다음 서비스들을 활성화해야 합니다.

1. **Project Initialization**
* 프로젝트 생성 (예: `roadcapture`) 및 Google Analytics 활성화
* Android 앱 등록 (`com.tenacy.roadcapture`)
* SHA-1 지문 등록 (Debug/Release keystore)
* `google-services.json` 파일을 다운로드하여 `app/` 폴더에 배치


2. **Authentication**
* Sign-in method 활성화: Google, Facebook, Custom(Kakao용 OpenID Connect)
* **Kakao OIDC 설정**:
* Issuer URL: `https://kauth.kakao.com`
* Client ID: 카카오 네이티브 앱 키
* Client Secret: 카카오 클라이언트 시크릿




3. **Cloud Functions**
* Node.js 환경 설정 및 다음 트리거 함수 배포:
* `delete-album.js`: 앨범 삭제 시 관련 데이터(Storage 이미지 등) 연쇄 삭제 (Transaction)
* `auth-triggers.js`: 계정 탈퇴 시 사용자 데이터 정리
* `reset-daily-credits.js`: 타임존 기반 자정 크레딧 초기화 (Scheduled Job)
* `verify-subscription.js`: Google Play Billing 영수증 검증




4. **Algolia Search Extension**
* Firebase Extensions 마켓플레이스에서 'Search with Algolia' 설치
* `albums`, `scraps` 컬렉션 인덱싱 설정



## Option 2: Minimal Configuration

외부 API 키가 없거나, 단순히 UI 및 로컬 기능을 테스트하고 싶을 때 사용합니다. 주요 외부 의존성을 제거하여 빌드 오류를 방지합니다.

### 1. app/build.gradle Modification

외부 서비스 관련 플러그인과 의존성을 주석 처리합니다.

```groovy
plugins {
    id 'com.android.application'
    // id 'com.google.android.libraries.mapsplatform.secrets-gradle-plugin'  // Disable
    // id 'com.google.gms.google-services'                                   // Disable
    id 'dagger.hilt.android.plugin'
    // ...
}

android {
    defaultConfig {
        // buildConfigField 로 선언된 API 키 관련 설정 제거
    }
    
    signingConfigs {
        // release 블록 전체 주석 처리
    }
}

dependencies {
    // 1. Remove Social Login SDKs
    // implementation libs.oauth (Naver)
    // implementation libs.v2.all (Kakao) ...

    // 2. Remove Firebase SDKs
    // implementation platform(libs.firebase.bom) ...

    // 3. Remove Map & Location SDKs
    // implementation libs.play.services.maps ...
    
    // 4. Remove AdMob & Branch SDKs
}

```

### 2. Project build.gradle & settings.gradle

프로젝트 수준의 플러그인 설정과 저장소 설정을 수정합니다.

```groovy
// build.gradle (Project)
plugins {
    // alias(libs.plugins.mapsplatform.secrets.gradle.plugin) apply false // Disable
    // alias(libs.plugins.google.services) apply false                    // Disable
}

// settings.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // maven { url 'https://...' } // 외부 API 관련 저장소 제거
    }
}