package com.tenacy.roadcapture.util

import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.LocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 더미 로케이션 데이터를 생성하고 데이터베이스에 저장하는 유틸리티 클래스
 */
@Singleton
class LocationDummyGenerator @Inject constructor(
    private val locationDao: LocationDao,
) {

    /**
     * 지정된 수의 더미 로케이션 데이터를 생성하고 데이터베이스에 저장
     *
     * @param locationDao 로케이션 데이터를 저장할 DAO
     * @param count 생성할 더미 데이터 개수
     * @param startArea 로케이션 데이터 생성 시작 지역 좌표 (기본값: 서울)
     * @param maxOffsetKm 시작 지역에서 최대 거리 오프셋 (킬로미터)
     * @return 생성된 로케이션 데이터 ID 목록
     */
    suspend fun generateDummyLocations(
        count: Int = 3000,
        startArea: Pair<Double, Double> = Pair(37.5665, 126.9780), // 서울 좌표
        maxOffsetKm: Double = 50.0
    ): List<Long> = withContext(Dispatchers.IO) {
        val locationIds = mutableListOf<Long>()
        val random = Random(System.currentTimeMillis())

        // 시작 날짜 설정 (30일 전)
        val startDateTime = LocalDateTime.now().minusDays(30)

        // 위도/경도 오프셋 계산 (대략적인 범위)
        // 위도 1도 = 약 111km, 경도 1도 = 약 88km (한국 위치 기준)
        val maxLatOffset = maxOffsetKm / 111.0
        val maxLngOffset = maxOffsetKm / 88.0

        repeat(count) { index ->
            // 랜덤 좌표 생성
            val latOffset = random.nextDouble(-maxLatOffset, maxLatOffset)
            val lngOffset = random.nextDouble(-maxLngOffset, maxLngOffset)

            val latitude = startArea.first + latOffset
            val longitude = startArea.second + lngOffset

            // 날짜 설정 (최근 30일 내에서 랜덤)
            val minutesToAdd = random.nextLong(30 * 24 * 60) // 30일을 분 단위로
            val createdAt = startDateTime.plusMinutes(minutesToAdd)

            // 로케이션 엔티티 생성
            val locationEntity = LocationEntity(
                coordinates = getCustomLocationFrom(latitude, longitude),
                createdAt = createdAt
            )

            // 데이터베이스에 저장
            val id = locationDao.insert(locationEntity)
            locationIds.add(id)

            // 매 500개마다 진행 상황 출력 (로깅용)
            if ((index + 1) % 500 == 0) {
                println("더미 로케이션 데이터 생성 중: ${index + 1}/$count")
            }
        }

        println("총 ${locationIds.size}개의 더미 로케이션 데이터 생성 완료")
        return@withContext locationIds
    }

    /**
     * 실제 도로 경로를 시뮬레이션하는 더미 로케이션 데이터 생성
     *
     * @param locationDao 로케이션 데이터를 저장할 DAO
     * @param count 생성할 경로 데이터 개수
     * @param pathCount 각 경로별 포인트 개수
     * @return 생성된 경로별 로케이션 ID 목록
     */
    suspend fun generateDummyPaths(
        count: Int = 10,
        pathCount: Int = 300 // 각 경로당 약 300개 포인트
    ): List<List<Long>> = withContext(Dispatchers.IO) {
        val random = Random(System.currentTimeMillis())
        val pathsList = mutableListOf<List<Long>>()

        // 각 경로 생성
        repeat(count) { pathIndex ->
            val pathIds = mutableListOf<Long>()

            // 경로 시작점 랜덤 선택 (한국 주요 도시)
            val startPoints = listOf(
                Pair(37.5665, 126.9780), // 서울
                Pair(35.1796, 129.0756), // 부산
                Pair(37.4563, 126.7052), // 인천
                Pair(35.8714, 128.6014), // 대구
                Pair(35.1595, 126.8526), // 광주
                Pair(36.3504, 127.3845), // 대전
                Pair(37.8747, 127.7342)  // 춘천
            )

            val startPoint = startPoints[random.nextInt(startPoints.size)]

            // 시작 시간 (최근 30일 내 랜덤)
            val startDateTime = LocalDateTime.now().minusDays(random.nextLong(1, 30))

            // 경로 방향 벡터 (임의 방향)
            val directionLat = random.nextDouble(-1.0, 1.0) * 0.0001 // 작은 변화량
            val directionLng = random.nextDouble(-1.0, 1.0) * 0.0001

            var currentLat = startPoint.first
            var currentLng = startPoint.second
            var currentTime = startDateTime

            // 각 경로 포인트 생성
            repeat(pathCount) { pointIndex ->
                // 약간의 랜덤성 추가 (자연스러운 경로를 위해)
                val latJitter = random.nextDouble(-0.00005, 0.00005)
                val lngJitter = random.nextDouble(-0.00005, 0.00005)

                currentLat += directionLat + latJitter
                currentLng += directionLng + lngJitter

                // 시간 간격 추가 (1-3초)
                currentTime = currentTime.plusSeconds(random.nextLong(1, 3))

                // 로케이션 엔티티 생성 및 저장
                val locationEntity = LocationEntity(
                    coordinates = getCustomLocationFrom(currentLat, currentLng),
                    createdAt = currentTime
                )

                val id = locationDao.insert(locationEntity)
                pathIds.add(id)
            }

            pathsList.add(pathIds)
            println("더미 경로 ${pathIndex + 1}/$count 생성 완료 (${pathIds.size} 포인트)")
        }

        val totalPoints = pathsList.sumOf { it.size }
        println("총 ${count}개 경로, ${totalPoints}개 로케이션 포인트 생성 완료")

        return@withContext pathsList
    }
}