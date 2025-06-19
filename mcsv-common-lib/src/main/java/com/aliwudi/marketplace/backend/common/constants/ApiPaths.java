package com.aliwudi.marketplace.backend.common.constants;

/**
 * Defines compile-time constants for API paths and authorization roles.
 * Using constants helps centralize URL management, improve readability,
 * and provide compile-time safety for endpoint definitions.
 */
public final class ApiPaths {

    // Private constructor to prevent instantiation
    private ApiPaths() {
        // Utility class
    }

    // --- Base Paths for Controllers ---
    public static final String USER_CONTROLLER_BASE = "/api/users";
    public static final String ROLE_CONTROLLER_BASE = "/api/roles";
    public static final String PRODUCT_CONTROLLER_BASE = "/api/products";
    public static final String REVIEW_CONTROLLER_BASE = "/api/reviews";
    public static final String SELLER_CONTROLLER_BASE = "/api/sellers";
    public static final String STORE_CONTROLLER_BASE = "/api/stores";
    public static final String CART_CONTROLLER_BASE = "/api/cart";
    public static final String INVENTORY_CONTROLLER_BASE = "/api/inventory";
    public static final String ORDER_CONTROLLER_BASE = "/api/orders";
    public static final String PAYMENT_CONTROLLER_BASE = "/api/payments";
    public static final String NOTIFICATION_CONTROLLER_BASE = "/api/notifications"; // Added for NotificationController
    public static final String DELIVERY_CONTROLLER_BASE = "/api/deliveries";     // Added for DeliveryController
    public static final String MEDIA_CONTROLLER_BASE = "/api/media";        // Added for MediaController

    // --- User Endpoints (from previous modification) ---
    public static final String USER_PROFILES_CREATE = "/profiles/create";
    public static final String USER_PROFILES_UPDATE = "/profiles/update/{id}";
    public static final String USER_PROFILES_DELETE = "/profiles/delete/{id}";
    public static final String USER_PROFILES_DELETE_ROLLBACK = "/profiles/delete-to-rollback/{authId}";
    public static final String USER_GET_BY_ID = "/{id}";
    public static final String USER_GET_BY_USERNAME = "/byUsername/{username}";
    public static final String USER_GET_BY_EMAIL = "/byEmail/{email}";
    public static final String USER_ADMIN_ALL = "/admin/all";
    public static final String USER_ADMIN_COUNT = "/admin/count";
    public static final String USER_ADMIN_BY_FIRST_NAME = "/admin/byFirstName";
    public static final String USER_ADMIN_COUNT_BY_FIRST_NAME = "/admin/countByFirstName";
    public static final String USER_ADMIN_BY_LAST_NAME = "/admin/byLastName";
    public static final String USER_ADMIN_COUNT_BY_LAST_NAME = "/admin/countByLastName";
    public static final String USER_ADMIN_BY_USERNAME_OR_EMAIL = "/admin/byUsernameOrEmail";
    public static final String USER_ADMIN_COUNT_BY_USERNAME_OR_EMAIL = "/admin/countByUsernameOrEmail";
    public static final String USER_ADMIN_BY_CREATED_AT_AFTER = "/admin/byCreatedAtAfter";
    public static final String USER_ADMIN_COUNT_BY_CREATED_AT_AFTER = "/admin/countByCreatedAtAfter";
    public static final String USER_ADMIN_BY_SHIPPING_ADDRESS = "/admin/byShippingAddress";
    public static final String USER_ADMIN_COUNT_BY_SHIPPING_ADDRESS = "/admin/countByShippingAddress";
    public static final String USER_EXISTS_BY_EMAIL = "/existsByEmail";
    public static final String USER_EXISTS_BY_USERNAME = "/existsByUsername";

    // --- Product Endpoints (from previous modification) ---
    public static final String PRODUCT_CREATE = "";
    public static final String PRODUCT_UPDATE = "/{id}";
    public static final String PRODUCT_DECREASE_STOCK = "/{productId}/decrease-stock";
    public static final String PRODUCT_GET_ALL = "";
    public static final String PRODUCT_COUNT_ALL = "/count";
    public static final String PRODUCT_GET_BY_ID = "/{id}";
    public static final String PRODUCT_DELETE = "/{id}";
    public static final String PRODUCT_GET_BY_STORE = "/store/{storeId}";
    public static final String PRODUCT_COUNT_BY_STORE = "/store/{storeId}/count";
    public static final String PRODUCT_GET_BY_SELLER = "/seller/{sellerId}";
    public static final String PRODUCT_COUNT_BY_SELLER = "/seller/{sellerId}/count";
    public static final String PRODUCT_GET_BY_CATEGORY = "/category/{category}";
    public static final String PRODUCT_COUNT_BY_CATEGORY = "/category/{category}/count";
    public static final String PRODUCT_GET_BY_PRICE_RANGE = "/price-range";
    public static final String PRODUCT_COUNT_BY_PRICE_RANGE = "/price-range/count";
    public static final String PRODUCT_GET_BY_STORE_AND_CATEGORY = "/store/{storeId}/category/{category}";
    public static final String PRODUCT_COUNT_BY_STORE_AND_CATEGORY = "/store/{storeId}/category/{category}/count";
    public static final String PRODUCT_GET_BY_SELLER_AND_CATEGORY = "/seller/{sellerId}/category/{category}";
    public static final String PRODUCT_GET_BY_CATEGORY_AND_PRICE_BETWEEN = "/category/{category}/price-range";
    public static final String PRODUCT_SEARCH = "/search";
    public static final String PRODUCT_SEARCH_COUNT = "/search/count";
    public static final String PRODUCT_GET_BY_LOCATION_ID = "/location/{locationId}";
    public static final String PRODUCT_COUNT_BY_LOCATION_ID = "/location/{locationId}/count";
    public static final String PRODUCT_GET_BY_COUNTRY_AND_CITY = "/location/country/{country}/city/{city}";
    public static final String PRODUCT_COUNT_BY_COUNTRY_AND_CITY = "/location/country/{country}/city/{city}/count";

    // --- Review Endpoints (from previous modification) ---
    public static final String REVIEW_SUBMIT = "";
    public static final String REVIEW_UPDATE = "/{id}";
    public static final String REVIEW_DELETE = "/{id}";
    public static final String REVIEW_GET_BY_ID = "/{id}";
    public static final String REVIEW_GET_ALL = "";
    public static final String REVIEW_COUNT_ALL = "/count";
    public static final String REVIEW_GET_BY_PRODUCT = "/product/{productId}";
    public static final String REVIEW_COUNT_BY_PRODUCT = "/product/{productId}/count";
    public static final String REVIEW_GET_BY_USER = "/user/{userId}";
    public static final String REVIEW_COUNT_BY_USER = "/user/{userId}/count";
    public static final String REVIEW_GET_BY_PRODUCT_AND_MIN_RATING = "/product/{productId}/min-rating/{minRating}";
    public static final String REVIEW_COUNT_BY_PRODUCT_AND_MIN_RATING = "/product/{productId}/min-rating/{minRating}/count";
    public static final String REVIEW_GET_LATEST_BY_PRODUCT = "/product/{productId}/latest";
    public static final String REVIEW_GET_BY_USER_AND_PRODUCT = "/user/{userId}/product/{productId}";
    public static final String REVIEW_GET_AVERAGE_RATING_FOR_PRODUCT = "/product/{productId}/average-rating";
    public static final String REVIEW_CHECK_EXISTS = "/exists/user/{userId}/product/{productId}";

    // --- Seller Endpoints (from previous modification) ---
    public static final String SELLER_GET_ALL = "";
    public static final String SELLER_COUNT_ALL = "/count";
    public static final String SELLER_GET_BY_ID = "/{id}";
    public static final String SELLER_CREATE = "";
    public static final String SELLER_UPDATE = "/{id}";
    public static final String SELLER_DELETE = "/{id}";
    public static final String SELLER_SEARCH = "/search";
    public static final String SELLER_SEARCH_COUNT = "/search/count";

    // --- Store Endpoints (from previous modification) ---
    public static final String STORE_CREATE = "";
    public static final String STORE_GET_ALL = "";
    public static final String STORE_COUNT_ALL = "/count";
    public static final String STORE_GET_BY_ID = "/{id}";
    public static final String STORE_GET_BY_SELLER = "/by-seller/{sellerId}";
    public static final String STORE_COUNT_BY_SELLER = "/by-seller/{sellerId}/count";
    public static final String STORE_UPDATE = "/{id}";
    public static final String STORE_DELETE = "/{id}";
    public static final String STORE_SEARCH_BY_NAME = "/search/name";
    public static final String STORE_SEARCH_BY_NAME_COUNT = "/search/name/count";
    public static final String STORE_SEARCH_BY_LOCATION_ID = "/search/locationId";
    public static final String STORE_SEARCH_BY_LOCATION_ID_COUNT = "/search/locationId/count";
    public static final String STORE_GET_BY_MIN_RATING = "/min-rating/{minRating}";
    public static final String STORE_COUNT_BY_MIN_RATING = "/min-rating/{minRating}/count";
    public static final String STORE_CHECK_EXISTS_NAME_AND_SELLER = "/exists/name-and-seller";

    // --- Cart Endpoints (from previous modification) ---
    public static final String CART_ADD_PRODUCT = "/add";
    public static final String CART_GET_USER_CART = "";
    public static final String CART_UPDATE_ITEM_QUANTITY = "/update";
    public static final String CART_REMOVE_PRODUCT = "/remove";
    public static final String CART_CLEAR = "/clear";
    public static final String CART_ITEMS_GET_ALL = "/items/all";
    public static final String CART_ITEMS_GET_BY_CART = "/items/byCart/{cartId}";
    public static final String CART_ITEMS_GET_BY_PRODUCT = "/items/byProduct/{productId}";
    public static final String CART_ITEMS_FIND_SPECIFIC = "/items/find/{cartId}/{productId}";
    public static final String CART_ITEMS_COUNT_ALL = "/items/count/all";
    public static final String CART_ITEMS_COUNT_BY_CART = "/items/count/byCart/{cartId}";
    public static final String CART_ITEMS_COUNT_BY_PRODUCT = "/items/count/byProduct/{productId}";
    public static final String CART_ITEMS_CHECK_EXISTS = "/items/exists/{cartId}/{productId}";
    public static final String CART_ITEMS_ADMIN_DELETE_BY_CART = "/items/admin/deleteByCart/{cartId}";
    public static final String CART_ITEMS_ADMIN_DELETE_BY_USER_AND_PRODUCT = "/items/admin/deleteByUserAndProduct/{userId}/{productId}";
    public static final String CART_ITEMS_ADMIN_DIRECT_UPDATE_QUANTITY = "/items/admin/directUpdateQuantity/{cartId}/{productId}";
    public static final String CART_ADMIN_GET_ALL = "/admin/all";
    public static final String CART_GET_BY_USER = "/byUser/{userId}";
    public static final String CART_COUNT_ALL = "/count";
    public static final String CART_CHECK_EXISTS_BY_USER = "/exists/byUser/{userId}";
    public static final String CART_ADMIN_DELETE_BY_USER = "/admin/deleteByUserId/{userId}";

    // --- Inventory Endpoints (from previous modification) ---
    public static final String INVENTORY_GET_AVAILABLE_STOCK = "/{productId}/available-stock";
    public static final String INVENTORY_CREATE_OR_UPDATE = "/add-or-update";
    public static final String INVENTORY_RESERVE = "/reserve";
    public static final String INVENTORY_RELEASE = "/release";
    public static final String INVENTORY_CONFIRM_DEDUCT = "/confirm-deduct";
    public static final String INVENTORY_ADMIN_GET_ALL = "/admin/all";
    public static final String INVENTORY_ADMIN_GET_AVAILABLE_GREATER_THAN = "/admin/availableGreaterThan/{quantity}";
    public static final String INVENTORY_ADMIN_DECREMENT_AVAILABLE = "/admin/decrement";
    public static final String INVENTORY_ADMIN_INCREMENT_AVAILABLE = "/admin/increment";
    public static final String INVENTORY_ADMIN_UPDATE_RESERVED = "/admin/updateReserved";
    public static final String INVENTORY_COUNT_ALL = "/count/all";
    public static final String INVENTORY_COUNT_AVAILABLE_GREATER_THAN = "/count/availableGreaterThan/{quantity}";
    public static final String INVENTORY_EXISTS_BY_PRODUCT = "/exists/{productId}";

    // --- Order Endpoints (from previous modification) ---
    public static final String ORDER_CHECKOUT = "/checkout";
    public static final String ORDER_GET_ALL = "";
    public static final String ORDER_GET_BY_ID = "/{id}";
    public static final String ORDER_GET_BY_USER = "/user/{userId}";
    public static final String ORDER_UPDATE_STATUS = "/{id}/status";
    public static final String ORDER_DELETE_BY_ID = "/{id}";
    public static final String ORDER_DELETE_BY_USER = "/user/{userId}";
    public static final String ORDER_DELETE_ITEM = "/order/{orderId}/item/{orderItemId}";
    public static final String ORDER_CLEAR_ALL = "/clearAll";
    public static final String ORDER_ITEMS_GET_ALL = "/items/all";
    public static final String ORDER_ITEMS_GET_BY_ORDER = "/items/byOrder/{orderId}";
    public static final String ORDER_ITEMS_GET_BY_PRODUCT = "/items/byProduct/{productId}";
    public static final String ORDER_ITEMS_FIND_SPECIFIC = "/items/find/{orderId}/{productId}";
    public static final String ORDER_ITEMS_COUNT_ALL = "/items/count/all";
    public static final String ORDER_ITEMS_COUNT_BY_ORDER = "/items/count/byOrder/{orderId}";
    public static final String ORDER_ITEMS_COUNT_BY_PRODUCT = "/items/count/byProduct/{productId}";
    public static final String ORDER_ITEMS_CHECK_EXISTS = "/items/exists/{orderId}/{productId}";
    public static final String ORDER_ADMIN_GET_ALL = "/admin/all";
    public static final String ORDER_ADMIN_GET_BY_USER = "/admin/byUser/{userId}";
    public static final String ORDER_ADMIN_GET_BY_STATUS = "/admin/byStatus/{status}";
    public static final String ORDER_ADMIN_GET_BY_TIME_RANGE = "/admin/byTimeRange";
    public static final String ORDER_ADMIN_GET_BY_USER_AND_STATUS = "/admin/byUserAndStatus/{userId}/{status}";
    public static final String ORDER_COUNT_ALL = "/count/all";
    public static final String ORDER_COUNT_BY_USER = "/count/byUser/{userId}";
    public static final String ORDER_COUNT_BY_STATUS = "/count/byStatus/{status}";
    public static final String ORDER_COUNT_BY_TIME_RANGE = "/count/byTimeRange";
    public static final String ORDER_COUNT_BY_USER_AND_STATUS = "/count/byUserAndStatus/{userId}/{status}";

    // --- Payment Endpoints (from previous modification) ---
    public static final String PAYMENT_INITIATE = "/initiate";
    public static final String PAYMENT_WEBHOOK = "/webhook/{transactionRef}";
    public static final String PAYMENT_GET_STATUS = "/{orderId}";
    public static final String PAYMENT_ADMIN_GET_ALL = "/admin/all";
    public static final String PAYMENT_ADMIN_GET_BY_USER = "/admin/byUser/{userId}";
    public static final String PAYMENT_ADMIN_GET_BY_STATUS = "/admin/byStatus/{status}";
    public static final String PAYMENT_ADMIN_GET_BY_TIME_RANGE = "/admin/byTimeRange";
    public static final String PAYMENT_GET_BY_TRANSACTION_REF = "/byTransactionRef/{transactionRef}";
    public static final String PAYMENT_COUNT_ALL = "/count/all";
    public static final String PAYMENT_COUNT_BY_USER = "/count/byUser/{userId}";
    public static final String PAYMENT_COUNT_BY_STATUS = "/count/byStatus/{status}";
    public static final String PAYMENT_COUNT_BY_TIME_RANGE = "/count/byTimeRange";
    public static final String PAYMENT_EXISTS_BY_TRANSACTION_REF = "/exists/byTransactionRef/{transactionRef}";

    // --- Notification Endpoints ---
    public static final String NOTIFICATION_CREATE = ""; // PostMapping on base
    public static final String NOTIFICATION_STREAM = "/stream";
    public static final String NOTIFICATION_ME_ALL = "/me";
    public static final String NOTIFICATION_ME_BY_STATUS = "/me/status/{status}";
    public static final String NOTIFICATION_ME_BY_TYPE = "/me/type/{type}";
    public static final String NOTIFICATION_ME_MARK_READ = "/me/{id}/read";
    public static final String NOTIFICATION_ME_DELETE = "/me/{id}";
    public static final String NOTIFICATION_ME_COUNT_ALL = "/me/count";
    public static final String NOTIFICATION_ME_COUNT_BY_STATUS = "/me/count/status/{status}";


    // --- Delivery Endpoints ---
    public static final String DELIVERY_CREATE = ""; // PostMapping on base
    public static final String DELIVERY_GET_BY_ORDER_ID = "/order/{orderId}";
    public static final String DELIVERY_GET_BY_TRACKING_NUMBER = "/track/{trackingNumber}";
    public static final String DELIVERY_UPDATE_STATUS = "/update-status";
    public static final String DELIVERY_CANCEL = "/cancel/{trackingNumber}";
    public static final String DELIVERY_ADMIN_DELETE = "/admin/{trackingNumber}";
    public static final String DELIVERY_ADMIN_GET_ALL = "/admin/all";
    public static final String DELIVERY_ADMIN_GET_BY_STATUS = "/admin/byStatus/{status}";
    public static final String DELIVERY_ADMIN_GET_BY_AGENT = "/admin/byAgent/{deliveryAgent}";
    public static final String DELIVERY_ADMIN_GET_ESTIMATED_BEFORE = "/admin/estimatedBefore";
    public static final String DELIVERY_ADMIN_GET_BY_LOCATION = "/admin/byLocation";
    public static final String DELIVERY_COUNT_ALL = "/count/all";
    public static final String DELIVERY_COUNT_BY_ORDER = "/count/byOrder/{orderId}";
    public static final String DELIVERY_COUNT_BY_STATUS = "/count/byStatus/{status}";
    public static final String DELIVERY_COUNT_BY_AGENT = "/count/byAgent/{deliveryAgent}";
    public static final String DELIVERY_COUNT_ESTIMATED_BEFORE = "/count/estimatedBefore";
    public static final String DELIVERY_COUNT_BY_LOCATION = "/count/byLocation";
    public static final String DELIVERY_EXISTS_BY_TRACKING_NUMBER = "/exists/{trackingNumber}";

    // --- Media Endpoints ---
    public static final String MEDIA_UPLOAD = "/upload";
    public static final String MEDIA_GET_BY_UNIQUE_FILE_NAME = "/{uniqueFileName}";
    public static final String MEDIA_GET_FOR_ENTITY = "/entity/{entityId}/{entityType}";
    public static final String MEDIA_COUNT_FOR_ENTITY = "/entity/{entityId}/{entityType}/count";
    public static final String MEDIA_ADMIN_DELETE = "/admin/{uniqueFileName}";
    public static final String MEDIA_ADMIN_GET_ALL = "/admin/all";
    public static final String MEDIA_ADMIN_GET_BY_ENTITY_TYPE = "/admin/byEntityType/{entityType}";
    public static final String MEDIA_ADMIN_GET_BY_FILE_TYPE = "/admin/byFileType/{fileType}";
    public static final String MEDIA_ADMIN_GET_BY_ASSET_NAME_CONTAINING = "/admin/byAssetNameContaining";
    public static final String MEDIA_COUNT_BY_ENTITY_TYPE = "/count/byEntityType/{entityType}";
    public static final String MEDIA_COUNT_BY_FILE_TYPE = "/count/byFileType/{fileType}";
    public static final String MEDIA_COUNT_BY_ASSET_NAME_CONTAINING = "/count/byAssetNameContaining";
    public static final String MEDIA_EXISTS_BY_UNIQUE_FILE_NAME = "/exists/{uniqueFileName}";


    // --- Authorization Roles ---
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_SELLER = "seller";
    public static final String ROLE_USER = "user";
    public static final String ROLE_USER_PROFILE_SYNC = "user-profile-sync"; // For Keycloak Event Listener
    public static final String ROLE_SERVICE = "service"; // For internal microservice calls
    public static final String ROLE_DELIVERY_AGENT = "delivery-agent"; // For delivery agents
}