package com.aliwudi.marketplace.backend.common.response;

public interface ApiResponseMessages {

    public String INVALID_SHIPPING_ADDRESS = "Invalid shipping address";
    public String INVALID_USERNAME = "Invalid username";
    public String INVALID_FIRST_NAME = "Invalid first name";
    public String INVALID_LAST_NAME = "Invalid last name";
    public String INVALID_EMAIL = "Invalid email";
    public String NOTIFICATION_NOT_FOUND = "Notification not found";
    public String INVALID_NOTIFICATION_CREATION_REQUEST = " Invalid notification creation request";
    public String INVALID_NOTIFICATION_STATUS = "Invalid notification status";
    public String INVALID_NOTIFICATION_TYPE = "Invalid notification type";
    public String INVALID_NOTIFICATION_ID = "Invalid notification id";
    public String NOTIFICATION_NOT_FOUND_FOR_USER = "Notification not found for user";

    String INVALID_USER_CREATION_REQUEST = "Invalid user creation request";

    String USER_NOT_FOUND_EMAIL ="User not found with email - ";

    String USER_NOT_FOUND_USERNAME ="User not found with username - ";

    String ROLE_NOT_FOUND_MSG = "Role not found";
    String OLD_PASSWORD_MISMATCH = "Old password mismatch";


    // --- General / Common Messages ---
    String OPERATION_SUCCESSFUL = "Operation successful"; // Generic success message
    String INVALID_PAGINATION_PARAMETERS = "Invalid pagination parameters: offset must be non-negative, limit must be positive";
    String INVALID_SEARCH_TERM = "Invalid search term provided";
    String INVALID_PARAMETERS = "Invalid parameters provided";
    String GENERAL_SERVER_ERROR = "An unexpected server error occurred";
    String INVALID_DATE_FORMAT = "Invalid date format";
    String INVALID_LOCATION = "Invalid location";

    // --- Authentication & Authorization Messages ---
    String UNAUTHENTICATED_USER = "User is not authenticated";
    String INVALID_USER_ID_FORMAT = "Authenticated principal is not a valid user ID format";
    String INVALID_USER_ID = "Invalid user ID"; 
    String INVALID_AUTHORIZATION_ID = "Invalid authorization ID"; 
    String SECURITY_CONTEXT_NOT_FOUND = "Security context not found";
    String USERNAME_ALREADY_EXISTS = "Error: Username already exists!";
    String EMAIL_ALREADY_EXISTS = "Error: Email already exists!";
    String USER_REGISTERED_SUCCESS = "User registered successfully!";
    String ROLE_USER_NOT_FOUND = "Error: Default role 'ROLE_USER' is not found in the database. Please ensure it's configured";
    String ROLE_NOT_FOUND = "Error: Role is not found in the database"; // Append role name
    String INVALID_ROLE_PROVIDED = "Error: Invalid role provided"; // Append role name
    String ERROR_REGISTERING_USER = "An error occurred during user registration";
    String LOGIN_SUCCESS = "Login successful!";
    String INVALID_CREDENTIALS = "Invalid username or password";
    String ERROR_AUTHENTICATING_USER = "An error occurred during authentication";


    // --- User Management Messages ---
    // Success
    String USER_DETAILS_RETRIEVED_SUCCESS = "User details retrieved successfully";
    String USER_DETAILS_UPDATED_SUCCESS = "User details updated successfully";
    String USER_DELETED_SUCCESS = "User account deleted successfully";
    String USERS_RETRIEVED_SUCCESS = "All users retrieved successfully";
    String USER_RETRIEVED_SUCCESS = "User retrieved successfully";
    String USER_UPDATED_SUCCESS = "User updated successfully";
    String CURRENT_USER_RETRIEVED_SUCCESS = "Current user details retrieved successfully";
    String USER_COUNT_RETRIEVED_SUCCESS = "User count retrieved successfully";

    // Error
    String USER_NOT_FOUND = "User not found";
    String USER_NOT_FOUND_AUTH_CONTEXT = "User not found in authentication context"; // Specific for /me endpoint
    String ERROR_RETRIEVING_USER_DETAILS = "An error occurred while retrieving user details";
    String ERROR_UPDATING_USER_DETAILS = "An error occurred while updating user details";
    String ERROR_DELETING_USER = "An error occurred while deleting user account";
    String ERROR_RETRIEVING_ALL_USERS = "An error occurred while retrieving all users";
    String ERROR_RETRIEVING_USERS = "An error occurred while retrieving users"; // General
    String ERROR_RETRIEVING_USER = "An error occurred while retrieving user"; // General
    String ERROR_UPDATING_USER = "An error occurred while updating user"; // General
    String ERROR_COUNTING_USERS = "Error count users"; // Typo fix needed: "Error counting users"
    String INVALID_USER_UPDATE_REQUEST = "Invalid user update request: request body cannot be empty";


    // --- Role Management Messages ---
    // Success
    String ROLE_COUNT_RETRIEVED_SUCCESS = "Role count retrived successfully"; // Typo fix needed: "retrieved"
    String ROLE_RETRIEVED_SUCCESS = "Role retrieved successfully";

    // Error
    String ERROR_COUNTING_ROLES = "Error counting roles";
    String ERROR_RETRIEVING_ROLE = "Error retrieing role"; // Typo fix needed: "retrieving"


    // --- Seller Management Messages ---
    // Success
    String SELLER_CREATED_SUCCESS = "Seller created successfully";
    String SELLER_UPDATED_SUCCESS = "Seller updated successfully";
    String SELLER_DELETED_SUCCESS = "Seller deleted successfully";
    String SELLER_RETRIEVED_SUCCESS = "Seller retrieved successfully";
    String SELLERS_RETRIEVED_SUCCESS = "Sellers retrieved successfully";
    String SELLER_COUNT_RETRIEVED_SUCCESS = "Seller count retrieved successfully";

    // Error
    String SELLER_NOT_FOUND = "Seller not found"; // Append ID
    String SELLER_NOT_FOUND_BY_EMAIL = "Seller not found by email";
    String INVALID_SELLER_ID = "Invalid seller ID provided";
    String INVALID_SELLER_CREATION_REQUEST = "Invalid seller creation request: Name and email are required";
    String INVALID_SELLER_UPDATE_REQUEST = "Invalid seller update request: Name cannot be blank";
    String DUPLICATE_SELLER_EMAIL = "A seller with this email already exists";
    String ERROR_CREATING_SELLER = "An error occurred while creating the seller";
    String ERROR_UPDATING_SELLER = "An error occurred while updating the seller";
    String ERROR_DELETING_SELLER = "An error occurred while deleting the seller";
    String ERROR_RETRIEVING_SELLER = "An error occurred while retrieving the seller";
    String ERROR_RETRIEVING_SELLERS = "An error occurred while retrieving sellers";
    String ERROR_RETRIEVING_SELLER_COUNT = "An error occurred while retrieving seller count";
    String ERROR_SEARCHING_SELLERS = "An error occurred while searching for sellers";
    String ERROR_SEARCHING_SELLER_COUNT = "An error occurred while retrieving search seller count";


    // --- Store Management Messages ---
    // Success
    String STORE_CREATED_SUCCESS = "Store created successfully";
    String STORE_UPDATED_SUCCESS = "Store updated successfully";
    String STORE_DELETED_SUCCESS = "Store deleted successfully";
    String STORE_RETRIEVED_SUCCESS = "Store retrieved successfully";
    String STORES_RETRIEVED_SUCCESS = "Stores retrieved successfully";
    String STORE_COUNT_RETRIEVED_SUCCESS = "Store count retrieved successfully";
    String STORE_EXISTS_CHECK_SUCCESS = "Store existence check successful";

    // Error
    String STORE_NOT_FOUND = "Store not found"; // Append ID
    String DUPLICATE_STORE_NAME = "A store with this name already exists";
    String DUPLICATE_STORE_NAME_FOR_SELLER = "Duplicate store name for seller";
    String INVALID_STORE_CREATION_REQUEST = "Invalid store creation request: Name and seller ID are required";
    String ERROR_CREATING_STORE = "An error occurred while creating the store";
    String ERROR_UPDATING_STORE = "An error occurred while updating the store";
    String ERROR_DELETING_STORE = "An error occurred while deleting the store";
    String ERROR_RETRIEVING_STORE = "An error occurred while retrieving the store";
    String ERROR_RETRIEVING_STORES = "An error occurred while retrieving stores";
    String ERROR_RETRIEVING_STORES_BY_SELLER = "An error occurred while retrieving stores by seller";
    String ERROR_RETRIEVING_STORE_COUNT_BY_SELLER = "An error occurred while retrieving store count by seller";
    String ERROR_RETRIEVING_STORE_COUNT = "Error retrieving store count"; // General store count error
    String ERROR_CHECKING_STORE_EXISTENCE = "Error checking store existence";
    String ERROR_SEARCHING_STORE_COUNT = "Error searching store count";
    String ERROR_SEARCHING_STORES = "Error searching stores";


    // --- Product Management Messages ---
    // Success
    String PRODUCT_CREATED_SUCCESS = "Product created successfully";
    String PRODUCT_UPDATED_SUCCESS = "Product updated successfully";
    String PRODUCT_DELETED_SUCCESS = "Product deleted successfully";
    String PRODUCT_RETRIEVED_SUCCESS = "Product retrieved successfully";
    String PRODUCTS_RETRIEVED_SUCCESS = "Products retrieved successfully";
    String PRODUCT_COUNT_RETRIEVED_SUCCESS = "Product count retrieved successfully";
    String PRODUCT_STOCK_DECREASED_SUCCESS = "Product stock decrease successfully";

    // Error
    String PRODUCT_NOT_FOUND = "Product not found";
    String INVALID_PRODUCT_CREATION_REQUEST = "Invalid product creation request: Name, positive price, non-negative stock, and storeId are required";
    String INVALID_PRODUCT_DATA = "Invalid product data";
    String INVALID_PRODUCT_ID = "Invalid product ID provided";
    String INVALID_CATEGORY_NAME = "Invalid category name provided";
    String INVALID_STORE_ID = "Invalid store ID provided";
    String INVALID_PRICE_RANGE_PARAMETERS = "Invalid price parameters";
    String INVALID_STOCK_DECREMENT_QUANTITY = "Invalid stock decrement quantity";
    String INSUFFICIENT_STOCK = "Insufficient stock for product";
    String PRODUCT_STOCK_UPDATE_FAILED = "Product stock update failed";
    String ERROR_CREATING_PRODUCT = "An error occurred while creating the product";
    String ERROR_UPDATING_PRODUCT = "An error occurred while updating the product";
    String ERROR_DELETING_PRODUCT = "An error occurred while deleting the product";
    String ERROR_RETRIEVING_PRODUCT = "An error occurred while retrieving the product";
    String ERROR_RETRIEVING_PRODUCTS = "An error occurred while retrieving products";
    String ERROR_RETRIEVING_PRODUCT_COUNT = "An error occurred while retrieving product count";
    String ERROR_RETRIEVING_PRODUCTS_BY_CATEGORY = "An error occurred while retrieving products by category";
    String ERROR_RETRIEVING_PRODUCT_COUNT_BY_CATEGORY = "An error occurred while retrieving product count by category";
    String ERROR_RETRIEVING_PRODUCTS_BY_STORE = "An error occurred while retrieving products by store";
    String ERROR_RETRIEVING_PRODUCT_COUNT_BY_STORE = "An error occurred while retrieving product count by store";
    String ERROR_SEARCHING_PRODUCTS = "An error occurred while searching for products";
    String ERROR_SEARCHING_PRODUCT_COUNT = "An error occurred while retrieving search product count";
    String ERROR_RETRIEVING_PRODUCT_COUNT_BY_SELLER = "Error retrieving product count by seller";
    String ERROR_RETRIEVING_PRODUCT_COUNT_BY_PRICE_RANGE = "Error retrieving product count by price range";
    String ERROR_RETRIEVING_PRODUCTS_BY_PRICE_RANGE = "Error retrieving products by price range";
    String ERROR_RETRIEVING_PRODUCT_COUNT_BY_STORE_AND_CATEGORY = "Error retrieving product count by store and category";
    String ERROR_RETRIEVING_PRODUCTS_BY_STORE_AND_CATEGORY = "Error retrieving products by store and category";
    String ERROR_RETRIEVING_PRODUCTS_BY_SELLER = "Error retrieving products by seller";
    String ERROR_RETRIEVING_PRODUCTS_BY_SELLER_AND_CATEGORY = "Error retrieving products by seller and category";
    String ERROR_RETRIEVING_PRODUCTS_BY_CATEGORY_AND_PRICE_RANGE = "Error retrieving products by category and price range";


    // --- Order Management Messages ---
    // Success
    String ORDER_CREATED_SUCCESS = "Order created successfully";
    String ORDER_RETRIEVED_SUCCESS = "Order retrieved successfully";
    String ORDERS_RETRIEVED_SUCCESS = "Orders retrieved successfully";
    String ORDER_STATUS_UPDATED_SUCCESS = "Order status updated successfully";
    String ORDER_DELETED_SUCCESS = "Order deleted successfully";
    String ORDER_ITEMS_DELETED_SUCCESS = "Order item(s) deleted successfully";
    String ALL_ORDERS_CLEARED_SUCCESS = "All orders cleared successfully";
    String ORDER_COUNT_SUCCESS = "Order count retrieved successfully";
    String ORDER_ITEM_EXISTS_CHECK_SUCCESS = "Order item existence check successful";
    String ORDER_ITEM_COUNT_SUCCESS = "Order item count retrieved successfully";
    String ORDER_ITEM_RETRIEVED_SUCCESS = "Order item retrieved successfully";
    String ORDERS_DELETED_SUCCESS_FOR_USER = "Orders deleted successfully for user";

    // Error
    String ORDER_NOT_FOUND = "Order not found";
    String ORDER_NOT_FOUND_FOR_USER = "Order not found for user";
    String ORDER_ITEM_NOT_FOUND = "Order item not found";
    String INVALID_ORDER_ID = "Invalid order ID provided";
    String MISSING_NEW_STATUS_BAD_REQUEST = "Missing or empty 'newStatus' in request body";
    String INVALID_ORDER_STATUS_VALUE = "Invalid OrderStatus value"; // Append value
    String ERROR_DURING_ORDER_CREATION = "An error occurred during order creation";
    String ERROR_UPDATING_ORDER_STATUS = "An error occurred updating order status";
    String ERROR_RETRIEVING_ORDERS = "An error occurred retrieving orders";
    String ERROR_RETRIEVING_ORDER_ITEM = "Error retrieving order item";
    String ERROR_COUNTING_ORDERS = "Error counting orders";
    String ERROR_COUNTING_ORDER_ITEMS = "Error counting order items";
    String ERROR_CHECKING_ORDER_ITEM_EXISTENCE = "Error checking order item existence";
    String ERROR_DELETING_ORDER = "An error occurred deleting order";


    // --- Cart Management Messages ---
    // Success
    String CART_ITEM_ADDED_SUCCESS = "Product added/updated in cart successfully";
    String CART_RETRIEVED_SUCCESS = "Cart retrieved successfully";
    String CART_ITEM_UPDATED_SUCCESS = "Cart item quantity updated successfully";
    String CART_ITEM_REMOVED_SUCCESS = "Product removed from cart successfully";
    String CART_CLEARED_SUCCESS = "Cart cleared successfully";
    String CART_DELETED_SUCCESS = "Cart deleted successfully";
    String CART_EXISTS_CHECK_SUCCESS = "Cart existence check successful";
    String CART_ITEMS_DELETED_SUCCESS = "Cart items deleted successfully";
    String CART_ITEM_COUNT_SUCCESS = "Cart item count retrieved successfully";
    String CART_ITEM_RETRIEVED_SUCCESS = "Cart item retrieved successfully";
    String CART_COUNT_SUCCESS = "Cart count successfully";
    String CART_ITEM_EXISTS_CHECK_SUCCESS = "Cart item exists check successfully";
    String CART_ITEMS_RETRIEVED_SUCCESS = "Cart items retrieved successfully";

    // Error
    String CART_NOT_FOUND = "Cart not found";
    String CART_NOT_FOUND_FOR_USER = "Cart not found for user"; // Could be for empty cart too
    String CART_ITEM_NOT_FOUND = "Cart item not found";
    String PRODUCT_NOT_FOUND_IN_CART = "Product not found in cart"; // Append ID
    String INVALID_QUANTITY_PROVIDED = "Invalid quantity provided";
    String INVALID_CART_ADD_REQUEST = "Invalid request: productId and positive quantity are required";
    String INVALID_CART_UPDATE_REQUEST = "Invalid request: productId and non-negative quantity are required";
    String INVALID_CART_REMOVE_REQUEST = "Invalid request: productId is required";
    String ERROR_ADDING_CART_ITEM = "An error occurred while adding item to cart";
    String ERROR_RETRIEVING_CART_ITEMS = "Error retrieving cart items";
    String ERROR_CHECKING_CART_ITEM_EXISTENCE = "Error checking cart item existence";
    String ERROR_RETRIEVING_CART_ITEM = "Error retrieving cart item"; // General
    String ERROR_COUNTING_CART_ITEMS = "Error counting cart items"; // General
    String ERROR_RETRIEVING_CART = "An error occurred while retrieving the cart"; // General
    String ERROR_UPDATING_CART_ITEM = "An error occurred while updating cart item";
    String ERROR_REMOVING_CART_ITEM = "An error occurred while removing cart item";
    String ERROR_CLEARING_CART = "An error occurred while clearing the cart";
    String ERROR_CHECKING_CART_EXISTENCE = "Error checking cart existence";
    String ERROR_COUNTING_CARTS = "Error counting carts";
    String ERROR_DELETING_CART = "Error deleting cart";
    String ERROR_DELETING_CART_ITEMS = "Error deleting cart items";


    // --- Payment Management Messages ---
    // Success
    String PAYMENT_INITIATED_SUCCESS = "Payment initiated successfully. Awaiting gateway confirmation";
    String PAYMENT_CALLBACK_PROCESSED_SUCCESS = "Payment callback processed successfully";
    String PAYMENT_STATUS_FETCHED_SUCCESS = "Payment status fetched successfully";
    String PAYMENTS_RETRIVED_SUCCESS = "Payments retrieved successfully";

    // Error
    String PAYMENT_NOT_FOUND_FOR_ORDER = "Payment details not found for order"; // Append ID
    String PAYMENT_NOT_FOUND = "Payment not found with transaction reference"; // Append ID
    String INVALID_PAYMENT_INITIATION_REQUEST = "Invalid payment initiation request: orderId and positive amount are required";
    String INVALID_WEBHOOK_CALLBACK_REQUEST = "Invalid webhook callback request: transactionRef, status, and orderId are required";
    String INVALID_PAYMENT_STATUS_VALUE = "Invalid payment status value"; // Append value
    String MISSING_ORDER_ID_FOR_PAYMENT_STATUS = "Missing or empty orderId for payment status request";
    String ERROR_INITIATING_PAYMENT = "An error occurred during payment initiation";
    String ERROR_PROCESSING_CALLBACK = "An error occurred while processing payment callback";
    String ERROR_FETCHING_PAYMENT_STATUS = "An error occurred while fetching payment status";
    String ERROR_RETRIEVING_PAYMENTS = "Error retrieving payments";


    // --- Delivery Management Messages ---
    // Success
    String DELIVERY_CREATED_SUCCESS = "Delivery created successfully";
    String DELIVERY_RETRIEVED_SUCCESS = "Delivery retrieved successfully";
    String DELIVERY_STATUS_UPDATED_SUCCESS = "Delivery status updated successfully";
    String DELIVERY_CANCELED_SUCCESS = "Delivery canceled successfully";
    String DELIVERIES_RETRIEVED_SUCCESS = "Deliveries retrieved successfully";
    String DELIVERY_COUNT_RETRIEVED_SUCCESS = "Delivery count retrieved successfully";
    String DELIVERY_DELETED_SUCCESS = "Delivery deleted successfully";
    String DELIVERY_EXISTS_CHECK_SUCCESS = "Delivery existence check successful";


    // Error
    String ERROR_RETRIEVING_DELIVERY = "Error retrieving delivery"; // General
    String INVALID_DELIVERY_CREATION_REQUEST = "Invalid delivery creation request: Order ID, recipient name, address, delivery agent, and estimated delivery date are required";
    String INVALID_TRACKING_NUMBER = "Invalid tracking number provided";
    String INVALID_DELIVERY_STATUS_UPDATE_REQUEST = "Invalid delivery status update request: Tracking number and new status are required";
    String INVALID_DELIVERY_STATUS = "Invalid delivery status"; // Append status
    String DELIVERY_NOT_FOUND_FOR_ORDER = "Delivery not found for order"; // Append orderId
    String DELIVERY_NOT_FOUND_FOR_TRACKING = "Delivery not found for tracking number"; // Append trackingNumber
    String DELIVERY_NOT_FOUND_FOR_UPDATE = "Delivery not found for update with tracking number"; // Append trackingNumber
    String DELIVERY_NOT_FOUND_FOR_CANCEL = "Delivery not found for cancellation with tracking number";
    String DELIVERY_NOT_FOUND_FOR_DELETE = "Delivery not found for deletion with tracking number";
    String DELIVERY_ALREADY_EXISTS_FOR_ORDER = "A delivery already exists for order"; // Append orderId
    String INVALID_DELIVERY_STATUS_TRANSITION_FROM_DELIVERED = "Cannot change status from DELIVERED to any other status";
    String INVALID_DELIVERY_STATUS_TRANSITION_FROM_CANCELED = "Cannot change status from CANCELED to any other status";
    String INVALID_DELIVERY_STATUS_FOR_CANCELLATION = "Delivery cannot be canceled in current status";
    String INVALID_DELIVERY_AGENT = "Invalid delivery agent provided";
    String INVALID_DELIVERY_STATUS_TRANSITION_FROM_FAILED = "Invalid delivery status transition from failed";    
    String ERROR_CREATING_DELIVERY = "An error occurred while creating the delivery";
    String ERROR_RETRIEVING_DELIVERY_BY_ORDER = "An error occurred while retrieving delivery by order ID";
    String ERROR_RETRIEVING_DELIVERY_BY_TRACKING = "An error occurred while retrieving delivery by tracking number";
    String ERROR_UPDATING_DELIVERY_STATUS = "An error occurred while updating the delivery status";
    String ERROR_CANCELING_DELIVERY = "An error occurred while canceling the delivery";
    String ERROR_RETRIEVING_DELIVERIES_BY_AGENT = "An error occurred while retrieving deliveries by agent";
    String ERROR_COUNTING_DELIVERIES_BY_AGENT = "An error occurred while counting deliveries by agent";
    String ERROR_SEARCHING_DELIVERIES = "An error occurred while searching for deliveries";
    String ERROR_COUNTING_SEARCH_DELIVERIES = "An error occurred while counting search results for deliveries";
    String ERROR_RETRIEVING_ALL_DELIVERIES = "An error occurred while retrieving all deliveries";
    String ERROR_COUNTING_ALL_DELIVERIES = "An error occurred while counting all deliveries";
    String ERROR_CHECKING_DELIVERY_EXISTENCE = "Error checking delivery existence";
    String ERROR_COUNTING_DELIVERIES_BY_LOCATION = "Error counting deliveries by location";
    String ERROR_COUNTING_DELIVERIES_BY_ESTIMATED_DATE = "Error counting deliveries by estimated date";
    String ERROR_COUNTING_DELIVERIES_BY_STATUS = "Error counting deliveries by status";
    String ERROR_COUNTING_DELIVERIES_BY_ORDER = "Error counting deliveries by order";
    String ERROR_DELETING_DELIVERY = "An error occurred while deleting the delivery";


    // --- Media Management Messages ---
    // Success
    String MEDIA_UPLOAD_SUCCESS = "Media uploaded successfully";
    String MEDIA_RETRIEVED_SUCCESS = "Media asset retrieved successfully";
    String MEDIA_ASSETS_RETRIEVED_SUCCESS = "Media assets retrieved successfully";
    String MEDIA_COUNT_RETRIEVED_SUCCESS = "Media asset count retrieved successfully";
    String MEDIA_DELETED_SUCCESS = "Media asset deleted successfully";
    String MEDIA_EXISTS_CHECK_SUCCESS = "Media existence check successful";

    // Error
    String MEDIA_NOT_FOUND = "Media asset not found with unique file name"; // Append uniqueFileName
    String MEDIA_NOT_FOUND_FOR_DELETE = "Media asset not found for deletion with unique file name"; // Append uniqueFileName
    String INVALID_MEDIA_UPLOAD_REQUEST = "Invalid media upload request: assetName, fileContent, fileType, entityId, and entityType are required";
    String INVALID_UNIQUE_FILE_NAME = "Invalid unique file name provided";
    String INVALID_ENTITY_IDENTIFIERS = "Invalid entity ID or entity type provided";
    String UNSUPPORTED_FILE_TYPE = "Unsupported file type"; // Append fileType
    String INVALID_BASE64_CONTENT = "Invalid Base64 file content provided";
    String INVALID_ASSET_NAME = "Invalid asset name provided"; // General invalid asset name
    String INVALID_FILE_TYPE = "Invalid file type provided"; // General invalid file type
    String DUPLICATE_MEDIA_UNIQUE_FILE_NAME = "Duplicate Media unique file name";

    String ERROR_UPLOADING_MEDIA = "An error occurred while uploading media";
    String ERROR_RETRIEVING_MEDIA = "An error occurred while retrieving the media asset";
    String ERROR_RETRIEVING_ENTITY_MEDIA = "An error occurred while retrieving media assets for the entity";
    String ERROR_COUNTING_ENTITY_MEDIA = "An error occurred while counting media assets for the entity";
    String ERROR_DELETING_MEDIA = "An error occurred while deleting the media asset";
    String ERROR_RETRIEVING_ALL_MEDIA = "An error occurred while retrieving all media assets";
    String ERROR_COUNTING_ALL_MEDIA = "An error occurred while counting all media assets";
    String ERROR_CHECKING_MEDIA_EXISTENCE = "Error checking media existence";
    String ERROR_COUNTING_MEDIA_BY_ASSET_NAME = "Error counting media by asset name";
    String ERROR_COUNTING_MEDIA_BY_FILE_TYPE = "Error counting media by file type";


    // --- Inventory Management Messages ---
    // Success
    String INVENTORY_RETRIEVED_SUCCESS = "Inventory retrieved succesfully"; // Typo fix needed: "successfully"

    // Error
    String INVENTORY_NOT_FOUND = "Inventory not found";
    String ERROR_RETRIEVING_INVENTORYS = "Error retrieving inventoryies"; // Typo fix needed: "inventories"


    // --- Review Management Messages ---
    // Success
    String REVIEW_SUBMITTED_SUCCESS = "Review submitted successfully";
    String REVIEW_UPDATED_SUCCESS = "Review updated successfully";
    String REVIEW_DELETED_SUCCESS = "Review deleted successfully";
    String REVIEWS_RETRIEVED_SUCCESS = "Reviews retrieved successfully";
    String AVERAGE_RATING_RETRIEVED_SUCCESS = "Average rating retrieved successfully";
    String REVIEW_RETRIEVED_SUCCESS = "Review retriewed successfully"; // Typo fix needed: "retrieved"
    String REVIEW_EXISTS_CHECK_SUCCESS = "Review exist check successfully"; // Typo fix needed: "existence"
    String REVIEW_COUNT_RETRIEVED_SUCCESS = "Review count retrieved successfully";

    // Error
    String REVIEW_NOT_FOUND = "Review not found"; // Append ID
    String INVALID_REVIEW_SUBMISSION = "Invalid review submission: Product ID, User ID, and a rating between 1 and 5 are required";
    String INVALID_REVIEW_RATING = "Invalid review rating: Rating must be between 1 and 5";
    String INVALID_REVIEW_ID = "Invalid review ID provided";
    String INVALID_RATING_RANGE = "Invalid rating range. Rating must be between 1 and 5"; // Duplicates INVALID_REVIEW_RATING
    String DUPLICATE_REVIEW_SUBMISSION = "You have already submitted a review for this product";
    String ERROR_SUBMITTING_REVIEW = "An error occurred while submitting the review";
    String ERROR_UPDATING_REVIEW = "An error occurred while updating the review";
    String ERROR_DELETING_REVIEW = "An error occurred while deleting the review";
    String ERROR_RETRIEVING_REVIEWS = "Error Retrieving reviews"; // General
    String ERROR_RETRIEVING_REVIEWS_FOR_PRODUCT = "An error occurred while retrieving reviews for the product";
    String ERROR_RETRIEVING_REVIEWS_BY_USER = "An error occurred while retrieving reviews by user";
    String ERROR_RETRIEVING_AVERAGE_RATING = "An error occurred while retrieving the average rating";
    String ERROR_RETRIEVING_REVIEW_COUNT_BY_USER = "Error Retrieving review count by user";
    String ERROR_CHECKING_REVIEW_EXISTENCE = "Error checking review existence";
    String ERROR_RETRIEVING_REVIEW = "Error retrieving review"; // General
    String ERROR_RETRIEVING_REVIEW_COUNT_FOR_PRODUCT = "Error retrieving review count for product";
    String ERROR_RETRIEVING_REVIEW_COUNT = "Error retrieving review count"; // General

}