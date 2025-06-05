/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common;

import com.aliwudi.marketplace.backend.common.exception.ServiceUnavailableException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Import Flux
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author user
 */
public class Main {

// --- Dummy Data/Service Classes (same as before) ---
    class UserProfile {

        String id;
        String username;

        public UserProfile(String id, String username) {
            this.id = id;
            this.username = username;
        }

        @Override
        public String toString() {
            return "UserProfile{id='" + id + "', username='" + username + "'}";
        }
    }

    class Order {

        String orderId;
        double amount;

        public Order(String orderId, double amount) {
            this.orderId = orderId;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return "Order{orderId='" + orderId + "', amount=" + amount + '}';
        }
    }

    class DashboardData {

        UserProfile userProfile;
        Order latestOrder;
        boolean featureEnabled;

        public DashboardData(UserProfile userProfile, Order latestOrder, boolean featureEnabled) {
            this.userProfile = userProfile;
            this.latestOrder = latestOrder;
            this.featureEnabled = featureEnabled;
        }

        private DashboardData() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public String toString() {
            return "DashboardData{\n" + "  userProfile=" + userProfile + ",\n" + "  latestOrder=" + latestOrder + ",\n" + "  featureEnabled=" + featureEnabled + "\n}";
        }
    }

    class DataService {

        private Random random = new Random();

        public Mono<UserProfile> fetchUserProfile(String userId) {
            System.out.println("Fetching user profile for " + userId + "...");
            return Mono.just(new UserProfile(userId, "Alice Smith")).delayElement(Duration.ofMillis(20000));
        }

        public Mono<Order> fetchLatestOrder(String userId) {
            System.out.println("Fetching latest order for " + userId + "...");
            return Mono.just(new Order("ORD-" + random.nextInt(1000), 123.45)).delayElement(Duration.ofMillis(10000));
        }

        public Mono isFeatureXEnabled() {
            System.out.println("Checking feature X status...");
            return Mono.error(new Exception("This is an error"));
        }
    }

    void test() {
        DataService dataService = new DataService();
        String currentUserId = "user123";

        // Create a list of Monos, each returning a different type
        List<Mono<?>> dashboardDataMonos = new ArrayList<>();
        dashboardDataMonos.add(dataService.fetchUserProfile(currentUserId));
        dashboardDataMonos.add(dataService.fetchLatestOrder(currentUserId));
        dashboardDataMonos.add(dataService.isFeatureXEnabled());

        Mono finalResultsMono = Mono.zip(dashboardDataMonos,
                 array -> {
                     UserProfile userProfile = (UserProfile) array[0];
                     Order latestOrder = (Order) array[1];
                     Boolean featureEnabled = (Boolean) array[2];
                    
                    return new DashboardData(userProfile, latestOrder, featureEnabled);
                 }) // Custom combinator for TupleN to List
                ;

        // Original attempt's logic was almost there, but the direct `Mono.zip(List<Mono<?>>)` isn't there.
        // It's Flux.zip(List<Publisher>) which emits a Flux<List<Object>>
        finalResultsMono
                .map((Object result) -> {

                    //List objArr = (List) result;
                    //UserProfile userProfile = (UserProfile) objArr.get(0);
                    //Order latestOrder = (Order) result.get(1);
                    // Boolean featureEnabled = (Boolean) result.get(2);
                    System.out.println(result);
                    return result;
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Failed during  for order  from Order Service due to unexpected error: " + ((Exception)e).getMessage());
                    return Mono.error(new Exception("Failed to connect to Order Service during  for order ID "));
                })
                /*.doOnNext(dashboardData -> {
                System.out.println("\nDashboard data successfully combined:");
                System.out.println(dashboardData);
            })*/
                .subscribe(consumer -> {
                    System.out.println(consumer);
                });
        try {
            Thread.sleep(100000);
        } catch (InterruptedException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("\nDashboard data rendering complete.");
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.test();
    }

}
