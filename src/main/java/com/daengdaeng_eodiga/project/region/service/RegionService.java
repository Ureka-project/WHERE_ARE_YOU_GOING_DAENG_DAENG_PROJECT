package com.daengdaeng_eodiga.project.region.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.daengdaeng_eodiga.project.Global.exception.UserLandNotFoundException;
import com.daengdaeng_eodiga.project.notification.service.NotificationService;
import com.daengdaeng_eodiga.project.pet.dto.PetResponse;
import com.daengdaeng_eodiga.project.region.dto.CityDetailVisit;
import com.daengdaeng_eodiga.project.region.dto.RegionVisit;
import com.daengdaeng_eodiga.project.region.dto.RegionOwnerCityDetail;
import com.daengdaeng_eodiga.project.region.dto.RegionOwnerInfo;
import com.daengdaeng_eodiga.project.region.entity.RegionOwnerLog;
import com.daengdaeng_eodiga.project.region.entity.RegionVisitDay;
import com.daengdaeng_eodiga.project.region.entity.RegionVisitTotal;
import com.daengdaeng_eodiga.project.region.enums.Regions;
import com.daengdaeng_eodiga.project.region.repository.RegionOwnerLogRepository;
import com.daengdaeng_eodiga.project.region.repository.RegionVisitDayRepository;
import com.daengdaeng_eodiga.project.region.repository.RegionVisitTotalRepository;
import com.daengdaeng_eodiga.project.story.dto.MyLandsDto;
import com.daengdaeng_eodiga.project.story.dto.UserMyLandsDto;
import com.daengdaeng_eodiga.project.user.entity.User;
import com.daengdaeng_eodiga.project.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegionService {
	private final RegionOwnerLogRepository regionOwnerLogRepository;
	private final RedisTemplate<String, Integer> redisTemplate2;
	private final UserService userService;
	private final RegionVisitDayRepository regionVisitDayRepository;
	private final RegionVisitTotalRepository regionVisitTotalRepository;
	private final NotificationService notificationService;


	/**
	 * resion_owner_log 테이블에서 지역별 땅 주인을 조회한다.
	 * @return RegionOwnerCity
	 * @author 김가은
	 */

	public RegionVisit<RegionOwnerCityDetail> fetchRegionOwners() {
		List<RegionOwnerInfo> regionOwnerInfos = regionOwnerLogRepository.findRegionOwner();
		HashMap<String,HashMap<String, RegionOwnerCityDetail>> regionOwners = new HashMap<>();
		Map<String, Set<Integer>> regionOwnerLogIdsByCity = new HashMap<>();
		Map<Integer,String> cityDetailByRegionOwnerLogIds = new HashMap<>();
		Map<Integer,RegionOwnerCityDetail> regionOwnerCityDetails = new HashMap<>();
		Map<Integer,List<PetResponse>> petsByRegionOwnerLogIds = new HashMap<>();
		regionOwnerInfos.stream().forEach(roi ->{
			Set<Integer> ids = regionOwnerLogIdsByCity.getOrDefault(roi.getCity(),new HashSet<>());
			ids.add(roi.getId());
			regionOwnerLogIdsByCity.put(roi.getCity(),ids);
			cityDetailByRegionOwnerLogIds.put(roi.getId(),roi.getCityDetail());
			regionOwnerCityDetails.put(roi.getId(),new RegionOwnerCityDetail(roi.getUserId(),roi.getUserNickname(),roi.getCount(),null));
			List<PetResponse> pets = petsByRegionOwnerLogIds.getOrDefault(roi.getId(),new ArrayList<>());
			if(roi.getPetId() != null) {
				pets.add(new PetResponse(roi.getPetId(),roi.getPetName(),roi.getPetImage()));
				petsByRegionOwnerLogIds.put(roi.getId(),pets);
			}

		});
		regionOwnerLogIdsByCity.forEach((city,ids) ->{
			HashMap<String,RegionOwnerCityDetail> regionOwnerCities = new HashMap<>();
			ids.forEach(id ->{
				RegionOwnerCityDetail regionOwnerCityDetail = regionOwnerCityDetails.get(id);
				regionOwnerCityDetail.setPets(petsByRegionOwnerLogIds.get(id));
				regionOwnerCities.put(cityDetailByRegionOwnerLogIds.get(id), regionOwnerCityDetail);
			});
			regionOwners.put(city,regionOwnerCities);
		});
		for (Regions region : Regions.values()) {
			if(!regionOwners.containsKey(region.name())) {
				HashMap<String, RegionOwnerCityDetail> regionOwnerCities = new HashMap<>();
				region.getCityDetails().forEach(cityDetail ->{
					regionOwnerCities.put(cityDetail, null);
				});
				regionOwners.put(region.name(),regionOwnerCities);
			}else{
				region.getCityDetails().forEach(cityDetail ->{
					if(!regionOwners.get(region.name()).containsKey(cityDetail)){
						regionOwners.get(region.name()).put(cityDetail,null);
					}
				});
			}
		}
		RegionVisit<RegionOwnerCityDetail> regionVisit = new RegionVisit();
		regionVisit.setVisitInfo(regionOwners);

		return regionVisit;
	}

	/**
	 * 유저 자신의 땅 목록 조회
	 * @param userId
	 * @return UserMyLandsDto
	 * @author 하진서, 김가은
	 */
	public UserMyLandsDto fetchUserLands(int userId) {

		String nickname = userService.findUser(userId).getNickname();
		List<Object[]> results = regionOwnerLogRepository.findCityAndCityDetailByUserId(userId);
		if( results.isEmpty() ) {
			throw new UserLandNotFoundException();
		}

		Map<String, List<CityDetailVisit>> myLands = new LinkedHashMap<>();
		for (Object[] row : results) {
			String city = (String) row[0];
			String cityDetail = (String) row[1];
			int count = (int) row[2];
			CityDetailVisit cityDetailVisit = new CityDetailVisit(cityDetail, count);
			List<CityDetailVisit> cityDetailVisits = myLands.getOrDefault(city, new ArrayList<>());
			cityDetailVisits.add(cityDetailVisit);
			myLands.put(city,cityDetailVisits);
		}
		List<MyLandsDto> myLandsDtos = myLands.entrySet().stream()
			.map(entry -> new MyLandsDto(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());
		UserMyLandsDto userMyLandsDto = UserMyLandsDto.builder()
			.nickname(nickname)
			.lands(myLandsDtos)
			.build();
		return userMyLandsDto;
	}

	/**
	 * 유저의 지역별(cityDetail) 방문횟수를 조회한다.
	 *
	 * @author 김가은
	 */

	public RegionVisit<Integer> fetchUserCityDetailVisitCountForDB(int userId) {
		User user = userService.findUser(userId);
		HashMap<String, HashMap<String, Integer>> cityVisitCount = new HashMap<>();
		List<RegionVisitTotal> regionVisitTotals = regionVisitTotalRepository.findByUser(user);
		regionVisitTotals.stream().forEach(regionVisitTotal -> {
			String city = regionVisitTotal.getCity();
			String cityDetail = regionVisitTotal.getCityDetail();
			Integer count = regionVisitTotal.getCount();
			HashMap<String, Integer> cityDetailVisitCount = cityVisitCount.getOrDefault(city, new HashMap<>());
			cityDetailVisitCount.put(cityDetail, count);
			cityVisitCount.put(city, cityDetailVisitCount);
		});



		for (Regions region : Regions.values()) {
			String city = region.name();
			cityVisitCount.putIfAbsent(city, new HashMap<>());
			region.getCityDetails().forEach(cityDetail -> {
				cityVisitCount.get(city).putIfAbsent(cityDetail, 0);
			});
		}
		RegionVisit<Integer> regionVisit = new RegionVisit();
		regionVisit.setVisitInfo(cityVisitCount);
		return regionVisit;

	}

}
