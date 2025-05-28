// SellerController.java (CORRECTED createSeller method)
package com.aliwudi.marketplace.backend.product.controller;


import com.aliwudi.marketplace.backend.product.model.Seller;
import com.aliwudi.marketplace.backend.product.repository.SellerRepository;
import com.aliwudi.marketplace.backend.product.service.SellerService;
import org.springframework.data.domain.Page;    // NEW IMPORT
import org.springframework.data.domain.Pageable; // NEW IMPORT
import org.springframework.data.domain.Sort;     // NEW IMPORT
import org.springframework.data.web.PageableDefault; // NEW IMPORT
import org.springframework.data.web.SortDefault;     // NEW IMPORT
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List; // Keep this import for non-paginated methods if they still exist
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600) // Adjust for Flutter app's port
@RestController
@RequestMapping("/api/sellers")
public class SellerController {

    private final SellerRepository sellerRepository; // Keep if directly used in controller
    private final SellerService sellerService; // NEW: Inject SellerService

    @Autowired
    public SellerController(SellerRepository sellerRepository, SellerService sellerService) {
        this.sellerRepository = sellerRepository;
        this.sellerService = sellerService; // Initialize service
    }

    // Get all sellers - accessible by any authenticated user
    // MODIFIED: getAllSellers to support pagination and sorting
    // Example usage: GET /api/sellers?page=0&size=5&sort=name,desc
    @GetMapping
    public ResponseEntity<Page<Seller>> getAllSellers(
            @PageableDefault(page = 0, size = 10) // Default page 0, size 10
            @SortDefault(sort = "name", direction = Sort.Direction.ASC) // Default sort by name ascending
            Pageable pageable) {
        
        Page<Seller> sellers = sellerService.getAllSellers(pageable);
        return new ResponseEntity<>(sellers, HttpStatus.OK);
    }

    // Get seller by ID - accessible by any authenticated user
    @GetMapping("/{id}")
    public ResponseEntity<Seller> getSellerById(@PathVariable Long id) {
        Optional<Seller> sellerData = sellerService.getSellerById(id); // Use service
        return sellerData.map(seller -> new ResponseEntity<>(seller, HttpStatus.OK))
                         .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // Create a new seller - Only for ADMINS
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Seller> createSeller(@RequestBody Seller seller) {
        try {
            Seller _seller = sellerService.saveSeller(seller); // Use service
            return new ResponseEntity<>(_seller, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Update an existing seller - Only for ADMINS
    // Updates only the basic seller information e.g name
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Seller> updateSeller(@PathVariable Long id, @RequestBody Seller seller) {
        Optional<Seller> sellerData = sellerService.getSellerById(id); // Use service

        if (sellerData.isPresent()) {
            Seller _seller = sellerData.get();
            _seller.setName(seller.getName());
            //_seller.setEmail(seller.getEmail());//email is not updatable
            return new ResponseEntity<>(sellerService.saveSeller(_seller), HttpStatus.OK); // Use service
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // Delete a seller - Only for ADMINS
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HttpStatus> deleteSeller(@PathVariable Long id) {
        try {
            sellerService.deleteSeller(id); // Use service
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // NEW: Endpoint to search sellers by name with pagination and sorting
    // Example: GET /api/sellers/search?query=tech&page=0&size=5
    @GetMapping("/search")
    public ResponseEntity<Page<Seller>> searchSellers(
            @RequestParam String query,
            @PageableDefault(page = 0, size = 10)
            @SortDefault(sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<Seller> sellers = sellerService.searchSellers(query, pageable);
        return new ResponseEntity<>(sellers, HttpStatus.OK);
    }
}