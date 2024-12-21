package com.pollu.demo.controller;

import com.pollu.demo.services.FavoriteCityService;
import com.pollu.demo.dto.FavoriteCityDTO;
import com.pollu.demo.entities.FavoriteCity;
import com.pollu.demo.repositories.FavoriteCityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class FavoriteCityController {

    @Autowired
    private FavoriteCityService favoriteCityService;

    @Autowired
    private FavoriteCityRepository favoriteCityRepository;

    @PostMapping
    public ResponseEntity<?> addFavoriteCity(@RequestBody FavoriteCityDTO cityDTO, @RequestParam Long userId) {
        try {
            FavoriteCity city = favoriteCityService.addFavoriteCity(userId, cityDTO);
            return ResponseEntity.ok(city);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @GetMapping
    public ResponseEntity<?> getFavoriteCities(@RequestParam Long userId) {
        try {
            List<FavoriteCityDTO> cities = favoriteCityService.getFavoriteCities(userId);
            return ResponseEntity.ok(cities);
        } catch (Exception e) {
            log.error("Error getting favorite cities for userId={}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{cityId}")
    public ResponseEntity<?> removeFavoriteCity(@PathVariable Long cityId, @RequestParam Long userId) {
        log.info("Attempting to remove favorite city: cityId={}, userId={}", cityId, userId);
        
        try {
            if (!favoriteCityRepository.existsByIdAndUserId(cityId, userId)) {
                log.warn("Favorite city not found: cityId={}, userId={}", cityId, userId);
                return ResponseEntity.notFound().build();
            }
            
            favoriteCityService.removeFavoriteCity(userId, cityId);
            log.info("Successfully removed favorite city: cityId={}, userId={}", cityId, userId);
            return ResponseEntity.ok().body(Map.of("message", "Ville supprimée des favoris"));
        } catch (Exception e) {
            log.error("Error removing favorite city: cityId={}, userId={}, error={}", 
                      cityId, userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}