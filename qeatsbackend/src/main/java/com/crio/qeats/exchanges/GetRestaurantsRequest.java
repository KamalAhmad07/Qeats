/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.exchanges;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

// TODO: CRIO_TASK_MODULE_RESTAURANTSAPI
//  Implement GetRestaurantsRequest.
//  Complete the class such that it is able to deserialize the incoming query params from
//  REST API clients.
//  For instance, if a REST client calls API
//  /qeats/v1/restaurants?latitude=28.4900591&longitude=77.536386&searchFor=tamil,
//  this class should be able to deserialize lat/long and optional searchFor from that.
@Data
@NoArgsConstructor
@AllArgsConstructor
// @RequiredArgsConstructor
@Getter
@Setter
public class GetRestaurantsRequest {

     public GetRestaurantsRequest(double d, double e) {
          this.latitude = d;
          this.longitude = e;
     }



     @Max(90)
     @NotNull
     @Min(-90)
     private Double latitude;
     
     @Min(-180)
     @NotNull
     @Max(180)
     private Double longitude;

    
      
     private  String searchFor;
     

}

