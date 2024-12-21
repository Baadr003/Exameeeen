package com.pollu.demo.services;

import com.pollu.demo.dto.FavoriteCityDTO;
import com.pollu.demo.entities.FavoriteCity;
import com.pollu.demo.entities.User;
import com.pollu.demo.repositories.FavoriteCityRepository;
import com.pollu.demo.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FavoriteCityService {

    @Autowired
    private FavoriteCityRepository favoriteCityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PollutionService pollutionService;

    @Autowired
    private AlertService alertService;

    @Scheduled(fixedRate = 3600000)
    public void checkFavoriteCitiesAqi() {
        log.info("Starting scheduled check of favorite cities AQI at {}", LocalDateTime.now());
        List<FavoriteCity> cities = favoriteCityRepository.findAll();
        cities.forEach(city -> {
            try {
                var pollution = pollutionService.getCurrentPollution(
                    city.getLatitude(), 
                    city.getLongitude()
                );

                int newAqi = pollution.getList().get(0).getMain().getAqi();
                User user = city.getUser();
                int threshold = getUserAqiThreshold(user.getId());

                if (newAqi >= threshold) {
                    alertService.processAlert(user, city, newAqi);
                }

                city.setCurrentAqi(newAqi);
                city.setLastChecked(LocalDateTime.now());
                favoriteCityRepository.save(city);

            } catch (Exception e) {
                log.error("Error checking city {}: {}", city.getCityName(), e.getMessage());
            }
        });
    }

    @Cacheable(cacheNames = "userAqiThresholds", key = "#userId")
    public int getUserAqiThreshold(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return user.getAqiThreshold();
    }

    public FavoriteCity addFavoriteCity(Long userId, FavoriteCityDTO cityDTO) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (favoriteCityRepository.existsByUserIdAndLatitudeAndLongitude(
                userId, cityDTO.getLatitude(), cityDTO.getLongitude())) {
            throw new RuntimeException("Cette ville est déjà dans vos favoris");
        }

        FavoriteCity city = new FavoriteCity();
        city.setUser(user);
        city.setCityName(cityDTO.getCityName());
        city.setLatitude(cityDTO.getLatitude());
        city.setLongitude(cityDTO.getLongitude());
        city.setLastChecked(LocalDateTime.now());

        try {
            var pollution = pollutionService.getCurrentPollution(
                cityDTO.getLatitude(),
                cityDTO.getLongitude()
            );
            int currentAqi = pollution.getList().get(0).getMain().getAqi();
            city.setCurrentAqi(currentAqi);

            if (currentAqi >= user.getAqiThreshold()) {
                alertService.processAlert(user, city, currentAqi);
            }
        } catch (Exception e) {
            log.error("Error fetching initial AQI for city: {}", cityDTO.getCityName(), e);
        }

        return favoriteCityRepository.save(city);
    }

    public List<FavoriteCityDTO> getFavoriteCities(Long userId) {
        return favoriteCityRepository.findByUserId(userId)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }


    public void removeFavoriteCity(Long userId, Long cityId) {
        try {
            FavoriteCity city = favoriteCityRepository.findByIdAndUserId(cityId, userId)
                .orElseThrow(() -> new RuntimeException("Ville favorite non trouvée"));
            favoriteCityRepository.delete(city);
            log.info("Favorite city removed successfully: cityId={}, userId={}", cityId, userId);
        } catch (Exception e) {
            log.error("Error removing favorite city: cityId={}, userId={}, error={}", cityId, userId, e.getMessage());
            throw new RuntimeException("Erreur lors de la suppression de la ville favorite: " + e.getMessage());
        }
    }

    private FavoriteCityDTO convertToDTO(FavoriteCity city) {
        FavoriteCityDTO dto = new FavoriteCityDTO();
        dto.setId(city.getId());
        dto.setCityName(city.getCityName());
        dto.setLatitude(city.getLatitude());
        dto.setLongitude(city.getLongitude());
        dto.setCurrentAqi(city.getCurrentAqi());
        dto.setLastChecked(city.getLastChecked());
        return dto;
    }

    
}