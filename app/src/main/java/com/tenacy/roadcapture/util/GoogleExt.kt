package com.tenacy.roadcapture.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.tenacy.roadcapture.ui.TripFragment.PlaceLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume

/**
 * Geocoder를 사용하여 위도/경도로부터 주소 정보를 가져와 PlaceLocation 객체를 생성합니다.
 */
suspend fun Context.extractLocationData(latLng: LatLng?): PlaceLocation? = withContext(Dispatchers.IO) {
    try {
        if (latLng == null) return@withContext null

//        val locale = getLocaleFromLatLng(latLng)
//        val geocoder = Geocoder(this@extractLocationData, locale ?: Locale.US)
        val geocoder = Geocoder(this@extractLocationData, Locale.getDefault())
        val address = getAddressFromLocation(geocoder, latLng) ?: return@withContext null

        createPlaceLocationFromAddress(address, latLng)
    } catch (e: Exception) {
        Log.e("GeocoderUtils", "주소 변환 실패: ${e.message}")
        null
    }
}

/**
 * Geocoder를 사용하여 위도/경도로부터 주소를 가져옵니다.
 * Android 버전에 따라 다른 API를 사용합니다.
 */
private suspend fun getAddressFromLocation(geocoder: Geocoder, latLng: LatLng): Address? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 비동기 API 사용
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        continuation.resume(addresses[0])
                    } else {
                        continuation.resume(null)
                    }
                }
            }
        } else {
            // 이전 버전용 동기 API 사용
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            addresses?.firstOrNull()
        }
    } catch (e: Exception) {
        Log.e("GeocoderUtils", "Geocoder 오류: ${e.message}")
        null
    }
}

/**
 * Address 객체로부터 PlaceLocation 객체를 생성합니다.
 */
private fun createPlaceLocationFromAddress(address: Address, latLng: LatLng): PlaceLocation {
    // 주소 구성요소 추출
    val country = address.countryName ?: ""
    val region = address.adminArea
    val city = address.locality ?: address.subAdminArea
    val district = address.subLocality
    val street = address.thoroughfare
    val detail = address.subThoroughfare

    // 표시 이름 결정 (가장 구체적인 정보 사용)
    val name = when {
        !address.featureName.isNullOrEmpty() &&
                address.featureName != address.subThoroughfare -> address.featureName
        !street.isNullOrEmpty() -> {
            if (!detail.isNullOrEmpty()) "$street $detail" else street
        }
        !district.isNullOrEmpty() -> district
        !city.isNullOrEmpty() -> city
        !region.isNullOrEmpty() -> region
        else -> country
    }

    // 전체 주소 생성
    val formattedAddress = buildString {
        append(country)
        if (!region.isNullOrEmpty()) append(" $region")
        if (!city.isNullOrEmpty()) append(" $city")
        if (!district.isNullOrEmpty()) append(" $district")
        if (!street.isNullOrEmpty()) append(" $street")
        if (!detail.isNullOrEmpty()) append(" $detail")
    }

    // 고유 식별자 생성 (위도/경도 기반)
    val placeId = "geo:${latLng.latitude},${latLng.longitude}"

    return PlaceLocation(
        placeId = placeId,
        name = name,
        country = country,
        region = region,
        city = city,
        district = district,
        street = street,
        detail = detail,
        formattedAddress = formattedAddress.trim(),
        coordinates = latLng
    )
}

fun Context.getLocaleFromLatLng(latLng: LatLng): Locale? {
    try {
        val geocoder = Geocoder(this)

        // 좌표로부터 주소 정보 가져오기
        val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)

        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]

            // 국가 코드 가져오기
            val countryCode = address.countryCode

            if (!countryCode.isNullOrEmpty()) {
                // 언어 코드는 기본값으로 영어 사용, 더 정확한 언어 코드를 알 수 없음
                return getSupportedLocaleByCountryCode(countryCode)

                // 또는 국가에 따른 주요 언어 코드를 매핑해서 사용할 수도 있음
                // return getLocaleByCountryCode(countryCode)
            }
        }

        return null
    } catch (e: Exception) {
        Log.e("Locale", "Error getting locale from coordinates", e)
        return null
    }
}

fun getLocaleByCountryCode(countryCode: String): Locale {
    return when (countryCode.uppercase()) {
        // A
        "AF" -> Locale("ps", "AF") // 아프가니스탄 - 파슈토어
        "AL" -> Locale("sq", "AL") // 알바니아 - 알바니아어
        "DZ" -> Locale("ar", "DZ") // 알제리 - 아랍어
        "AD" -> Locale("ca", "AD") // 안도라 - 카탈루냐어
        "AO" -> Locale("pt", "AO") // 앙골라 - 포르투갈어
        "AG" -> Locale("en", "AG") // 앤티가 바부다 - 영어
        "AR" -> Locale("es", "AR") // 아르헨티나 - 스페인어
        "AM" -> Locale("hy", "AM") // 아르메니아 - 아르메니아어
        "AU" -> Locale("en", "AU") // 호주 - 영어
        "AT" -> Locale("de", "AT") // 오스트리아 - 독일어
        "AZ" -> Locale("az", "AZ") // 아제르바이잔 - 아제르바이잔어

        // B
        "BS" -> Locale("en", "BS") // 바하마 - 영어
        "BH" -> Locale("ar", "BH") // 바레인 - 아랍어
        "BD" -> Locale("bn", "BD") // 방글라데시 - 벵골어
        "BB" -> Locale("en", "BB") // 바베이도스 - 영어
        "BY" -> Locale("be", "BY") // 벨라루스 - 벨라루스어
        "BE" -> Locale("nl", "BE") // 벨기에 - 네덜란드어(플람어)
        "BZ" -> Locale("en", "BZ") // 벨리즈 - 영어
        "BJ" -> Locale("fr", "BJ") // 베냉 - 프랑스어
        "BT" -> Locale("dz", "BT") // 부탄 - 종카어
        "BO" -> Locale("es", "BO") // 볼리비아 - 스페인어
        "BA" -> Locale("bs", "BA") // 보스니아 헤르체고비나 - 보스니아어
        "BW" -> Locale("en", "BW") // 보츠와나 - 영어
        "BR" -> Locale("pt", "BR") // 브라질 - 포르투갈어
        "BN" -> Locale("ms", "BN") // 브루나이 - 말레이어
        "BG" -> Locale("bg", "BG") // 불가리아 - 불가리아어
        "BF" -> Locale("fr", "BF") // 부르키나파소 - 프랑스어
        "BI" -> Locale("fr", "BI") // 부룬디 - 프랑스어

        // C
        "KH" -> Locale("km", "KH") // 캄보디아 - 크메르어
        "CM" -> Locale("fr", "CM") // 카메룬 - 프랑스어
        "CA" -> Locale("en", "CA") // 캐나다 - 영어
        "CV" -> Locale("pt", "CV") // 카보베르데 - 포르투갈어
        "CF" -> Locale("fr", "CF") // 중앙아프리카공화국 - 프랑스어
        "TD" -> Locale("fr", "TD") // 차드 - 프랑스어
        "CL" -> Locale("es", "CL") // 칠레 - 스페인어
        "CN" -> Locale.SIMPLIFIED_CHINESE // 중국 - 중국어 간체
        "CO" -> Locale("es", "CO") // 콜롬비아 - 스페인어
        "KM" -> Locale("ar", "KM") // 코모로 - 아랍어
        "CG" -> Locale("fr", "CG") // 콩고 - 프랑스어
        "CD" -> Locale("fr", "CD") // 콩고민주공화국 - 프랑스어
        "CR" -> Locale("es", "CR") // 코스타리카 - 스페인어
        "CI" -> Locale("fr", "CI") // 코트디부아르 - 프랑스어
        "HR" -> Locale("hr", "HR") // 크로아티아 - 크로아티아어
        "CU" -> Locale("es", "CU") // 쿠바 - 스페인어
        "CY" -> Locale("el", "CY") // 키프로스 - 그리스어
        "CZ" -> Locale("cs", "CZ") // 체코 - 체코어

        // D
        "DK" -> Locale("da", "DK") // 덴마크 - 덴마크어
        "DJ" -> Locale("fr", "DJ") // 지부티 - 프랑스어
        "DM" -> Locale("en", "DM") // 도미니카 - 영어
        "DO" -> Locale("es", "DO") // 도미니카공화국 - 스페인어

        // E
        "EC" -> Locale("es", "EC") // 에콰도르 - 스페인어
        "EG" -> Locale("ar", "EG") // 이집트 - 아랍어
        "SV" -> Locale("es", "SV") // 엘살바도르 - 스페인어
        "GQ" -> Locale("es", "GQ") // 적도기니 - 스페인어
        "ER" -> Locale("ti", "ER") // 에리트레아 - 티그리냐어
        "EE" -> Locale("et", "EE") // 에스토니아 - 에스토니아어
        "ET" -> Locale("am", "ET") // 에티오피아 - 암하라어

        // F
        "FJ" -> Locale("en", "FJ") // 피지 - 영어
        "FI" -> Locale("fi", "FI") // 핀란드 - 핀란드어
        "FR" -> Locale.FRANCE // 프랑스 - 프랑스어

        // G
        "GA" -> Locale("fr", "GA") // 가봉 - 프랑스어
        "GM" -> Locale("en", "GM") // 감비아 - 영어
        "GE" -> Locale("ka", "GE") // 조지아 - 조지아어
        "DE" -> Locale.GERMANY // 독일 - 독일어
        "GH" -> Locale("en", "GH") // 가나 - 영어
        "GR" -> Locale("el", "GR") // 그리스 - 그리스어
        "GD" -> Locale("en", "GD") // 그레나다 - 영어
        "GT" -> Locale("es", "GT") // 과테말라 - 스페인어
        "GN" -> Locale("fr", "GN") // 기니 - 프랑스어
        "GW" -> Locale("pt", "GW") // 기니비사우 - 포르투갈어
        "GY" -> Locale("en", "GY") // 가이아나 - 영어

        // H
        "HT" -> Locale("fr", "HT") // 아이티 - 프랑스어
        "HN" -> Locale("es", "HN") // 온두라스 - 스페인어
        "HK" -> Locale("zh", "HK") // 홍콩 - 중국어(번체)
        "HU" -> Locale("hu", "HU") // 헝가리 - 헝가리어

        // I
        "IS" -> Locale("is", "IS") // 아이슬란드 - 아이슬란드어
        "IN" -> Locale("hi", "IN") // 인도 - 힌디어
        "ID" -> Locale("id", "ID") // 인도네시아 - 인도네시아어
        "IR" -> Locale("fa", "IR") // 이란 - 페르시아어
        "IQ" -> Locale("ar", "IQ") // 이라크 - 아랍어
        "IE" -> Locale("en", "IE") // 아일랜드 - 영어
        "IL" -> Locale("he", "IL") // 이스라엘 - 히브리어
        "IT" -> Locale.ITALY // 이탈리아 - 이탈리아어

        // J
        "JM" -> Locale("en", "JM") // 자메이카 - 영어
        "JP" -> Locale.JAPAN // 일본 - 일본어
        "JO" -> Locale("ar", "JO") // 요르단 - 아랍어

        // K
        "KZ" -> Locale("kk", "KZ") // 카자흐스탄 - 카자흐어
        "KE" -> Locale("sw", "KE") // 케냐 - 스와힐리어
        "KI" -> Locale("en", "KI") // 키리바시 - 영어
        "KP" -> Locale("ko", "KP") // 북한 - 한국어
        "KR" -> Locale.KOREA // 대한민국 - 한국어
        "KW" -> Locale("ar", "KW") // 쿠웨이트 - 아랍어
        "KG" -> Locale("ky", "KG") // 키르기스스탄 - 키르기스어

        // L
        "LA" -> Locale("lo", "LA") // 라오스 - 라오어
        "LV" -> Locale("lv", "LV") // 라트비아 - 라트비아어
        "LB" -> Locale("ar", "LB") // 레바논 - 아랍어
        "LS" -> Locale("st", "LS") // 레소토 - 소토어
        "LR" -> Locale("en", "LR") // 라이베리아 - 영어
        "LY" -> Locale("ar", "LY") // 리비아 - 아랍어
        "LI" -> Locale("de", "LI") // 리히텐슈타인 - 독일어
        "LT" -> Locale("lt", "LT") // 리투아니아 - 리투아니아어
        "LU" -> Locale("lb", "LU") // 룩셈부르크 - 룩셈부르크어

        // M
        "MO" -> Locale("zh", "MO") // 마카오 - 중국어
        "MK" -> Locale("mk", "MK") // 북마케도니아 - 마케도니아어
        "MG" -> Locale("mg", "MG") // 마다가스카르 - 말라가시어
        "MW" -> Locale("ny", "MW") // 말라위 - 치체와어
        "MY" -> Locale("ms", "MY") // 말레이시아 - 말레이어
        "MV" -> Locale("dv", "MV") // 몰디브 - 디베히어
        "ML" -> Locale("fr", "ML") // 말리 - 프랑스어
        "MT" -> Locale("mt", "MT") // 몰타 - 몰타어
        "MH" -> Locale("en", "MH") // 마셜 제도 - 영어
        "MR" -> Locale("ar", "MR") // 모리타니 - 아랍어
        "MU" -> Locale("en", "MU") // 모리셔스 - 영어
        "MX" -> Locale("es", "MX") // 멕시코 - 스페인어
        "FM" -> Locale("en", "FM") // 미크로네시아 - 영어
        "MD" -> Locale("ro", "MD") // 몰도바 - 루마니아어
        "MC" -> Locale("fr", "MC") // 모나코 - 프랑스어
        "MN" -> Locale("mn", "MN") // 몽골 - 몽골어
        "ME" -> Locale("sr", "ME") // 몬테네그로 - 세르비아어
        "MA" -> Locale("ar", "MA") // 모로코 - 아랍어
        "MZ" -> Locale("pt", "MZ") // 모잠비크 - 포르투갈어
        "MM" -> Locale("my", "MM") // 미얀마 - 버마어

        // N
        "NA" -> Locale("en", "NA") // 나미비아 - 영어
        "NR" -> Locale("na", "NR") // 나우루 - 나우루어
        "NP" -> Locale("ne", "NP") // 네팔 - 네팔어
        "NL" -> Locale("nl", "NL") // 네덜란드 - 네덜란드어
        "NZ" -> Locale("en", "NZ") // 뉴질랜드 - 영어
        "NI" -> Locale("es", "NI") // 니카라과 - 스페인어
        "NE" -> Locale("fr", "NE") // 니제르 - 프랑스어
        "NG" -> Locale("en", "NG") // 나이지리아 - 영어
        "NO" -> Locale("no", "NO") // 노르웨이 - 노르웨이어

        // O
        "OM" -> Locale("ar", "OM") // 오만 - 아랍어

        // P
        "PK" -> Locale("ur", "PK") // 파키스탄 - 우르두어
        "PW" -> Locale("en", "PW") // 팔라우 - 영어
        "PS" -> Locale("ar", "PS") // 팔레스타인 - 아랍어
        "PA" -> Locale("es", "PA") // 파나마 - 스페인어
        "PG" -> Locale("en", "PG") // 파푸아뉴기니 - 영어
        "PY" -> Locale("es", "PY") // 파라과이 - 스페인어
        "PE" -> Locale("es", "PE") // 페루 - 스페인어
        "PH" -> Locale("tl", "PH") // 필리핀 - 타갈로그어
        "PL" -> Locale("pl", "PL") // 폴란드 - 폴란드어
        "PT" -> Locale("pt", "PT") // 포르투갈 - 포르투갈어

        // Q
        "QA" -> Locale("ar", "QA") // 카타르 - 아랍어

        // R
        "RO" -> Locale("ro", "RO") // 루마니아 - 루마니아어
        "RU" -> Locale("ru", "RU") // 러시아 - 러시아어
        "RW" -> Locale("rw", "RW") // 르완다 - 키냐르완다어

        // S
        "KN" -> Locale("en", "KN") // 세인트키츠 네비스 - 영어
        "LC" -> Locale("en", "LC") // 세인트루시아 - 영어
        "VC" -> Locale("en", "VC") // 세인트빈센트 그레나딘 - 영어
        "WS" -> Locale("sm", "WS") // 사모아 - 사모아어
        "SM" -> Locale("it", "SM") // 산마리노 - 이탈리아어
        "ST" -> Locale("pt", "ST") // 상투메 프린시페 - 포르투갈어
        "SA" -> Locale("ar", "SA") // 사우디아라비아 - 아랍어
        "SN" -> Locale("fr", "SN") // 세네갈 - 프랑스어
        "RS" -> Locale("sr", "RS") // 세르비아 - 세르비아어
        "SC" -> Locale("fr", "SC") // 세이셸 - 프랑스어
        "SL" -> Locale("en", "SL") // 시에라리온 - 영어
        "SG" -> Locale("en", "SG") // 싱가포르 - 영어
        "SK" -> Locale("sk", "SK") // 슬로바키아 - 슬로바키아어
        "SI" -> Locale("sl", "SI") // 슬로베니아 - 슬로베니아어
        "SB" -> Locale("en", "SB") // 솔로몬 제도 - 영어
        "SO" -> Locale("so", "SO") // 소말리아 - 소말리어
        "ZA" -> Locale("en", "ZA") // 남아프리카공화국 - 영어
        "SS" -> Locale("en", "SS") // 남수단 - 영어
        "ES" -> Locale("es", "ES") // 스페인 - 스페인어
        "LK" -> Locale("si", "LK") // 스리랑카 - 싱할라어
        "SD" -> Locale("ar", "SD") // 수단 - 아랍어
        "SR" -> Locale("nl", "SR") // 수리남 - 네덜란드어
        "SZ" -> Locale("en", "SZ") // 에스와티니 - 영어
        "SE" -> Locale("sv", "SE") // 스웨덴 - 스웨덴어
        "CH" -> Locale("de", "CH") // 스위스 - 독일어
        "SY" -> Locale("ar", "SY") // 시리아 - 아랍어

        // T
        "TW" -> Locale.TRADITIONAL_CHINESE // 대만 - 중국어(번체)
        "TJ" -> Locale("tg", "TJ") // 타지키스탄 - 타지크어
        "TZ" -> Locale("sw", "TZ") // 탄자니아 - 스와힐리어
        "TH" -> Locale("th", "TH") // 태국 - 태국어
        "TL" -> Locale("pt", "TL") // 동티모르 - 포르투갈어
        "TG" -> Locale("fr", "TG") // 토고 - 프랑스어
        "TO" -> Locale("to", "TO") // 통가 - 통가어
        "TT" -> Locale("en", "TT") // 트리니다드 토바고 - 영어
        "TN" -> Locale("ar", "TN") // 튀니지 - 아랍어
        "TR" -> Locale("tr", "TR") // 터키 - 터키어
        "TM" -> Locale("tk", "TM") // 투르크메니스탄 - 투르크멘어
        "TV" -> Locale("en", "TV") // 투발루 - 영어

        // U
        "UG" -> Locale("en", "UG") // 우간다 - 영어
        "UA" -> Locale("uk", "UA") // 우크라이나 - 우크라이나어
        "AE" -> Locale("ar", "AE") // 아랍에미리트 - 아랍어
        "GB" -> Locale.UK // 영국 - 영어
        "US" -> Locale.US // 미국 - 영어
        "UY" -> Locale("es", "UY") // 우루과이 - 스페인어
        "UZ" -> Locale("uz", "UZ") // 우즈베키스탄 - 우즈베크어

        // V
        "VU" -> Locale("bi", "VU") // 바누아투 - 비슬라마어
        "VA" -> Locale("it", "VA") // 바티칸 - 이탈리아어
        "VE" -> Locale("es", "VE") // 베네수엘라 - 스페인어
        "VN" -> Locale("vi", "VN") // 베트남 - 베트남어

        // Y
        "YE" -> Locale("ar", "YE") // 예멘 - 아랍어

        // Z
        "ZM" -> Locale("en", "ZM") // 잠비아 - 영어
        "ZW" -> Locale("en", "ZW") // 짐바브웨 - 영어

        // 그 외 주요 지역
        "HK" -> Locale("zh", "HK") // 홍콩 - 중국어(번체)
        "MO" -> Locale("zh", "MO") // 마카오 - 중국어(번체)
        "PR" -> Locale("es", "PR") // 푸에르토리코 - 스페인어
        "GU" -> Locale("en", "GU") // 괌 - 영어
        "VI" -> Locale("en", "VI") // 미국령 버진아일랜드 - 영어

        // 기본값은 영어(미국)로 설정
        else -> Locale.US
    }
}

fun getSupportedLocaleByCountryCode(countryCode: String): Locale {
    val proposedLocale = getLocaleByCountryCode(countryCode)

    // 시스템에서 지원하는 Locale 목록 가져오기
    val availableLocales = Locale.getAvailableLocales().toList()

    // 제안된 Locale이 지원되는지 확인
    val isSupported = availableLocales.any {
        it.language == proposedLocale.language && it.country == proposedLocale.country
    }

    // 지원되지 않으면 영어 또는 다른 대안 사용
    return if (isSupported) proposedLocale else Locale.US
}