
/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.services;

import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.repositoryservices.RestaurantRepositoryService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class RestaurantServiceImpl implements RestaurantService {

  private final Double peakHoursServingRadiusInKms = 3.0;
  private final Double normalHoursServingRadiusInKms = 5.0;
 
  @Autowired
  private RestaurantRepositoryService restaurantRepositoryService;


  // TODO: CRIO_TASK_MODULE_RESTAURANTSAPI - Implement findAllRestaurantsCloseby.
  
  // Check RestaurantService.java file for the interface contract.
  @Override
  public GetRestaurantsResponse findAllRestaurantsCloseBy(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
   
          
    List<Restaurant> restaurants;
  
        if( (currentTime.isAfter(LocalTime.parse("07:59:59")) && currentTime.isBefore(LocalTime.parse("10:00:01"))) ||
           (currentTime.isAfter(LocalTime.parse("12:59:59")) && currentTime.isBefore(LocalTime.parse("14:00:01"))) ||
           (currentTime.isAfter(LocalTime.parse("18:59:59")) && currentTime.isBefore(LocalTime.parse("21:00:01"))) ) {
    
            restaurants = restaurantRepositoryService.findAllRestaurantsCloseBy(getRestaurantsRequest.getLatitude(),
                         getRestaurantsRequest.getLongitude() ,currentTime, peakHoursServingRadiusInKms );                        
          }else{
            restaurants = restaurantRepositoryService.findAllRestaurantsCloseBy(getRestaurantsRequest.getLatitude(),
                         getRestaurantsRequest.getLongitude() ,currentTime, normalHoursServingRadiusInKms ); 
          }

          log.info("inside  the  Resturant clss class !!!! ");
        
     GetRestaurantsResponse response = new GetRestaurantsResponse();
     response.setRestaurants(restaurants);



         return response;
        }


}

