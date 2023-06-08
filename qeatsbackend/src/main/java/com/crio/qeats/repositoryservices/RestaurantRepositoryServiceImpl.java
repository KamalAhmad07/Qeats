/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import redis.clients.jedis.Jedis;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.ItemEntity;
import com.crio.qeats.models.MenuEntity;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.repositories.ItemRepository;
import com.crio.qeats.repositories.MenuRepository;
import com.crio.qeats.repositories.RestaurantRepository;
import com.crio.qeats.utils.GeoLocation;
import com.crio.qeats.utils.GeoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;


@Service
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {


  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private RedisConfiguration redisConfiguration;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private  ItemRepository itemRepository;

  @Autowired
  private MenuRepository  menuRepository;

  @Autowired
  private Provider<ModelMapper> modelMapperProvider;

  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
  }

  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objectives:
  // 1. Implement findAllRestaurantsCloseby.
  // 2. Remember to keep the precision of GeoHash in mind while using it as a key.
  // Check RestaurantRepositoryService.java file for the interface contract.
  
  public List<Restaurant> findAllRestaurantsMongo(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {

        ModelMapper modelMapper = modelMapperProvider.get();
        
        List<RestaurantEntity> restaurantEntityList = restaurantRepository.findAll();
    
        List<Restaurant> restaurantList = new ArrayList<>();
        for (RestaurantEntity restaurantEntity : restaurantEntityList) {
    
          if (isOpenNow(currentTime, restaurantEntity)) {
            if (GeoUtils.findDistanceInKm(latitude, longitude,
                    restaurantEntity.getLatitude(), restaurantEntity.getLongitude())
                    < servingRadiusInKms) {
              restaurantList.add(modelMapper.map(restaurantEntity, Restaurant.class));
            }
          }
        }
    
        return restaurantList;    
  }

  
  private List<Restaurant> findAllRestaurantsCache(Double latitude, Double longitude,
      LocalTime currentTime, Double servingRadiusInKms) {
   
        List<Restaurant> restaurantList = new ArrayList<>();

        GeoLocation geoLocation = new GeoLocation(latitude, longitude);
        GeoHash geoHash = GeoHash.withCharacterPrecision(geoLocation.getLatitude(),
                geoLocation.getLongitude(), 7);
  
        Jedis jedis = null;
        try {
          jedis = redisConfiguration.getJedisPool().getResource();
          String jsonStringFromCache = jedis.get(geoHash.toBase32());
    
          if (jsonStringFromCache == null) {
            // Cache needs to be updated.
            String createdJsonString = "";
            try {
              restaurantList = findAllRestaurantsMongo(geoLocation.getLatitude(),
                  geoLocation.getLongitude(), currentTime, servingRadiusInKms);
              createdJsonString = new ObjectMapper().writeValueAsString(restaurantList);
            } catch (JsonProcessingException e) {
              e.printStackTrace();
            }
    
            // Do operations with jedis resource
            jedis.setex(geoHash.toBase32(), GlobalConstants.REDIS_ENTRY_EXPIRY_IN_SECONDS,
                    createdJsonString);
          } else {
            try {
              restaurantList = new ObjectMapper().readValue(jsonStringFromCache,
                      new TypeReference<List<Restaurant>>(){});
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        } finally {
          if (jedis != null) {
            jedis.close();
          }
        }
    
        return restaurantList;    
  }
  
  
  public List<Restaurant> findAllRestaurantsCloseBy(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {
              
        if (redisConfiguration.isCacheAvailable()) {
          return findAllRestaurantsCache(latitude, longitude, currentTime, servingRadiusInKms);
        } else { 
          return findAllRestaurantsMongo(latitude, longitude, currentTime, servingRadiusInKms);
        }
    
    }



  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose names have an exact or partial match with the search query.
  @Override
  public List<Restaurant> findRestaurantsByName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

        Optional<List<RestaurantEntity>> resturantEntities  = restaurantRepository.findRestaurantsByNameExact(searchString);
        Set<String> set = new HashSet<>();
        List<Restaurant> restaurantList = new ArrayList<>();
        ModelMapper mapper = modelMapperProvider.get();

        if(resturantEntities.isPresent()){
          List<RestaurantEntity> entities  = resturantEntities.get();
          for(RestaurantEntity entity : entities){
               if(isRestaurantCloseByAndOpen(entity, currentTime, latitude, longitude, servingRadiusInKms)
                && !set.contains(entity.getId())){
                  restaurantList.add(mapper.map(entity, Restaurant.class));
                  set.add(entity.getId());
                }
          }
        }                             
     return restaurantList;
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose attributes (cuisines) intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByAttributes(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

        List<Restaurant> filteredRestaurants = new ArrayList<>(); 
        Optional<List<RestaurantEntity>> optionalRestaurantEntities  = restaurantRepository.findRestaurantsByNameExact(searchString);
        if (optionalRestaurantEntities.isPresent()) {
          List<RestaurantEntity> restaurantEntities = optionalRestaurantEntities.get();
          Set<String> set = new HashSet<>();
         
          // Step 2: Filter the list based on opening hours and proximity, and create a new list of Restaurant objects         
          ModelMapper mapper = modelMapperProvider.get();
          
          for (RestaurantEntity entity : restaurantEntities) {
              if (isRestaurantCloseByAndOpen(entity, currentTime, latitude, longitude, servingRadiusInKms) 
              && !set.contains(entity.getId())) {
                  filteredRestaurants.add(mapper.map(entity, Restaurant.class));
                  set.add(entity.getId());
              }
          }          
        // Step 3: Return the filtered list of restaurants         
      }
     return filteredRestaurants;  
  }
  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose names form a complete or partial match
  // with the search query.


  @Override
  public List<Restaurant> findRestaurantsByItemName(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

         // finding list of  item
        List<ItemEntity> itemEntities  = itemRepository.findItemsByItemName(searchString);
        List<String> itemId = itemEntities.stream()
        .map(ItemEntity::getId) 
        .collect(Collectors.toList());
        
        //  find menuEntity
        Optional<List<MenuEntity>> menuEntity = menuRepository.findMenusByItemsItemIdIn(itemId);
        List<MenuEntity> menuEntities = menuEntity.get();

        // find  Resturant  Id
        List<String> resturanId =  menuEntities.stream().map(e->e.getRestaurantId()).collect(Collectors.toList());

        List<RestaurantEntity> restaurantEntities = new ArrayList<>();

          for(String id : resturanId){
                restaurantEntities.add(restaurantRepository.findById(id).get());
          }

        Set<String> set = new HashSet<>();
        List<Restaurant> restaurantList = new ArrayList<>();
        ModelMapper mapper = modelMapperProvider.get();

        if(itemEntities.size() != 0){         
         for(RestaurantEntity entity: restaurantEntities){
          if( isRestaurantCloseByAndOpen(entity, currentTime, latitude, longitude, servingRadiusInKms) 
            && !set.contains(entity.getId())){
            restaurantList.add(mapper.map(entity, Restaurant.class));
            set.add(entity.getId());
          }
         }
        }                             
     return restaurantList; 
  }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose attributes intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByItemAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
   
        ModelMapper modelMapper = modelMapperProvider.get();
          // Step 1: Find the items matching the search query
  List<ItemEntity> matchingItems = itemRepository.findItemsByItemName(searchString);

  // Step 2: Collect the item IDs from the matching items
  Set<String> itemIds = matchingItems.stream()
      .map(ItemEntity::getId)
      .collect(Collectors.toSet());

  // Step 3: Find the menus containing the matching items
  Optional<List<MenuEntity>> matchingMenus = menuRepository.findMenusByItemsItemIdIn(new ArrayList<>(itemIds));
    List<MenuEntity> matchingMenusList  = matchingMenus.get();
  // Step 4: Collect the restaurant IDs from the matching menus
  Set<String> restaurantIds = matchingMenusList.stream()
      .map(MenuEntity::getRestaurantId)
      .collect(Collectors.toSet());

  // Step 5: Fetch the restaurants within the serving radius
  List<RestaurantEntity> restaurants =  new ArrayList<>();
  // List<String> resturantId = new ArrayList<>(restaurantIds);
    for(String id : restaurantIds){
         restaurants.add(restaurantRepository.findById(id).get());
        //  restaurants.add(restaurantRepository.findById(id).orElseThrow(NullPointerException::new));

    }
 
  // Step 6: Filter the restaurants based on the matching restaurant IDs
  List<Restaurant> matchingRestaurants = new ArrayList<>();
     matchingRestaurants   = restaurants.stream()
      .filter(restaurant -> isRestaurantCloseByAndOpen(restaurant, currentTime, latitude, longitude, servingRadiusInKms)  && restaurantIds.contains(restaurant.getId()))
      .map(restaurant -> modelMapper.map(restaurant , Restaurant.class))
      .collect(Collectors.toList());

  return matchingRestaurants;
    
  }





  /**
   * Utility method to check if a restaurant is within the serving radius at a given time.
   * @return boolean True if restaurant falls within serving radius and is open, false otherwise
   */
  private boolean isRestaurantCloseByAndOpen(RestaurantEntity restaurantEntity,
      LocalTime currentTime, Double latitude, Double longitude, Double servingRadiusInKms) {
    if (isOpenNow(currentTime, restaurantEntity)) {
      return GeoUtils.findDistanceInKm(latitude, longitude,
          restaurantEntity.getLatitude(), restaurantEntity.getLongitude())
          < servingRadiusInKms;
    }

    return false;
  }


  /////  async method  
  @Override
  public Future<List<Restaurant>> findRestaurantsByNameAsync(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    // TODO Auto-generated method stub
     List<Restaurant>  resturants = findRestaurantsByName(latitude, longitude, searchString, currentTime, servingRadiusInKms);
     return new AsyncResult<>(resturants);
  
  }

  @Override
  public Future<List<Restaurant>> findRestaurantsByAttributesAsync(Double latitude,
      Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
        List<Restaurant>  resturants = findRestaurantsByAttributes(latitude, longitude, searchString, currentTime, servingRadiusInKms);

        return new AsyncResult<>(resturants);
  }

  @Override
  public Future<List<Restaurant>> findRestaurantsByItemNameAsync(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    
        List<Restaurant>  resturants = findRestaurantsByItemName(latitude, longitude, searchString, currentTime, servingRadiusInKms);

        return new AsyncResult<>(resturants);
  }

  @Override
  public Future<List<Restaurant>> findRestaurantsByItemAttributesAsync(Double latitude,
      Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
        List<Restaurant>  resturants = findRestaurantsByItemAttributes(latitude, longitude, searchString, currentTime, servingRadiusInKms);
        return new AsyncResult<>(resturants);
  }


}
