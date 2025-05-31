package com.aliwudi.marketplace.backend.common.response;

public interface ApiResponseMessages {

    public String ERROR_RETRIEVING_PRODUCT_COUNT_BY_PRICE_RANGE = "Error retrieving product count by price range";
    public String ERROR_RETRIEVING_PRODUCTS_BY_PRICE_RANGE = "Error retrieving products by price range";
    public String ERROR_RETRIEVING_PRODUCT_COUNT_BY_STORE_AND_CATEGORY = "Error retrieving product count by store and category";
    public String ERROR_RETRIEVING_PRODUCTS_BY_STORE_AND_CATEGORY = "Error retrieving products by store and category";    
    public String ERROR_RETRIEVING_PRODUCTS_BY_SELLER = "Error retrieving products by seller.";
    public String ERROR_RETRIEVING_PRODUCTS_BY_SELLER_AND_CATEGORY = "Error retrieving products by seller and category.";

    // --- Success Messages ---
    String ORDER_CREATED_SUCCESS = "Order created successfully.";
    String ORDER_RETRIEVED_SUCCESS = "Order retrieved successfully.";
    String ORDERS_RETRIEVED_SUCCESS = "Orders retrieved successfully.";
    String ORDER_STATUS_UPDATED_SUCCESS = "Order status updated successfully.";
    String ORDER_DELETED_SUCCESS = "Order deleted successfully.";
    String ORDER_ITEMS_DELETED_SUCCESS = "Order item(s) deleted successfully.";
    String ALL_ORDERS_CLEARED_SUCCESS = "All orders cleared successfully.";
    String OPERATION_SUCCESSFUL = "Operation successful."; // Generic success message

    // --- Error Messages (Specific) ---
    String ORDER_NOT_FOUND = "Order not found with ID: "; // Append ID
    String USER_NOT_FOUND = "User not found with ID: "; // Append ID
    String PRODUCT_NOT_FOUND = "Product not found with ID: "; // Append ID
    String ORDER_ITEM_NOT_FOUND = "Order item not found with ID: "; // Append ID
    String INSUFFICIENT_STOCK = "Insufficient stock for product: "; // Append product details
    
    // --- Product Messages ---
    String PRODUCT_CREATED_SUCCESS = "Product created successfully.";
    String PRODUCT_UPDATED_SUCCESS = "Product updated successfully.";
    String PRODUCT_DELETED_SUCCESS = "Product deleted successfully.";
    String PRODUCT_RETRIEVED_SUCCESS = "Product retrieved successfully.";
    String PRODUCTS_RETRIEVED_SUCCESS = "Products retrieved successfully.";
    String PRODUCT_COUNT_RETRIEVED_SUCCESS = "Product count retrieved successfully.";

    // --- Product Error Messages ---
    String INVALID_PRODUCT_CREATION_REQUEST = "Invalid product creation request: Name, positive price, non-negative stock, and storeId are required.";
    String INVALID_PRODUCT_DATA = "Invalid product data: "; // Append specific reason
    String INVALID_PRODUCT_ID = "Invalid product ID provided.";
    String INVALID_CATEGORY_NAME = "Invalid category name provided.";
    String INVALID_STORE_ID = "Invalid store ID provided.";
    String ERROR_CREATING_PRODUCT = "An error occurred while creating the product.";
    String ERROR_UPDATING_PRODUCT = "An error occurred while updating the product.";
    String ERROR_DELETING_PRODUCT = "An error occurred while deleting the product.";
    String ERROR_RETRIEVING_PRODUCT = "An error occurred while retrieving the product.";
    String ERROR_RETRIEVING_PRODUCTS = "An error occurred while retrieving products.";
    String ERROR_RETRIEVING_PRODUCT_COUNT = "An error occurred while retrieving product count.";
    String ERROR_RETRIEVING_PRODUCTS_BY_CATEGORY = "An error occurred while retrieving products by category.";
    String ERROR_RETRIEVING_PRODUCT_COUNT_BY_CATEGORY = "An error occurred while retrieving product count by category.";
    String ERROR_RETRIEVING_PRODUCTS_BY_STORE = "An error occurred while retrieving products by store.";
    String ERROR_RETRIEVING_PRODUCT_COUNT_BY_STORE = "An error occurred while retrieving product count by store.";
    String ERROR_SEARCHING_PRODUCTS = "An error occurred while searching for products.";
    String ERROR_SEARCHING_PRODUCT_COUNT = "An error occurred while retrieving search product count.";
    // Product not found is already in common error messages (PRODUCT_NOT_FOUND)

    // --- Review Messages ---
    String REVIEW_SUBMITTED_SUCCESS = "Review submitted successfully.";
    String REVIEW_UPDATED_SUCCESS = "Review updated successfully.";
    String REVIEW_DELETED_SUCCESS = "Review deleted successfully.";
    String REVIEWS_RETRIEVED_SUCCESS = "Reviews retrieved successfully.";
    String AVERAGE_RATING_RETRIEVED_SUCCESS = "Average rating retrieved successfully.";

    // --- Review Error Messages ---
    String REVIEW_NOT_FOUND = "Review not found with ID: "; // Append ID
    String INVALID_REVIEW_SUBMISSION = "Invalid review submission: Product ID, User ID, and a rating between 1 and 5 are required.";
    String INVALID_REVIEW_RATING = "Invalid review rating: Rating must be between 1 and 5.";
    String INVALID_REVIEW_ID = "Invalid review ID provided.";
    String DUPLICATE_REVIEW_SUBMISSION = "You have already submitted a review for this product.";
    String ERROR_SUBMITTING_REVIEW = "An error occurred while submitting the review.";
    String ERROR_UPDATING_REVIEW = "An error occurred while updating the review.";
    String ERROR_DELETING_REVIEW = "An error occurred while deleting the review.";
    String ERROR_RETRIEVING_REVIEWS_FOR_PRODUCT = "An error occurred while retrieving reviews for the product.";
    String ERROR_RETRIEVING_REVIEWS_BY_USER = "An error occurred while retrieving reviews by user.";
    String ERROR_RETRIEVING_AVERAGE_RATING = "An error occurred while retrieving the average rating.";
    // --- Store Messages ---
    String STORE_CREATED_SUCCESS = "Store created successfully.";
    String STORE_UPDATED_SUCCESS = "Store updated successfully.";
    String STORE_DELETED_SUCCESS = "Store deleted successfully.";
    String STORE_RETRIEVED_SUCCESS = "Store retrieved successfully.";
    String STORES_RETRIEVED_SUCCESS = "Stores retrieved successfully.";
    String STORE_COUNT_RETRIEVED_SUCCESS = "Store count retrieved successfully.";

    // --- Store Error Messages ---
    String STORE_NOT_FOUND = "Store not found with ID: "; // Append ID
    String INVALID_STORE_CREATION_REQUEST = "Invalid store creation request: Name and seller ID are required.";
    String DUPLICATE_STORE_NAME = "A store with this name already exists.";
    String ERROR_CREATING_STORE = "An error occurred while creating the store.";
    String ERROR_UPDATING_STORE = "An error occurred while updating the store.";
    String ERROR_DELETING_STORE = "An error occurred while deleting the store.";
    String ERROR_RETRIEVING_STORE = "An error occurred while retrieving the store.";
    String ERROR_RETRIEVING_STORES = "An error occurred while retrieving stores.";
    String ERROR_RETRIEVING_STORES_BY_SELLER = "An error occurred while retrieving stores by seller.";
    String ERROR_RETRIEVING_STORE_COUNT_BY_SELLER = "An error occurred while retrieving store count by seller.";
    // --- Seller Messages ---
    String SELLER_CREATED_SUCCESS = "Seller created successfully.";
    String SELLER_UPDATED_SUCCESS = "Seller updated successfully.";
    String SELLER_DELETED_SUCCESS = "Seller deleted successfully.";
    String SELLER_RETRIEVED_SUCCESS = "Seller retrieved successfully.";
    String SELLERS_RETRIEVED_SUCCESS = "Sellers retrieved successfully.";
    String SELLER_COUNT_RETRIEVED_SUCCESS = "Seller count retrieved successfully.";

    // --- Seller Error Messages ---
    String SELLER_NOT_FOUND = "Seller not found with ID: "; // Append ID
    String INVALID_SELLER_CREATION_REQUEST = "Invalid seller creation request: Name and email are required.";
    String INVALID_SELLER_UPDATE_REQUEST = "Invalid seller update request: Name cannot be blank.";
    String INVALID_SELLER_ID = "Invalid seller ID provided.";
    String DUPLICATE_SELLER_EMAIL = "A seller with this email already exists.";
    String ERROR_CREATING_SELLER = "An error occurred while creating the seller.";
    String ERROR_UPDATING_SELLER = "An error occurred while updating the seller.";
    String ERROR_DELETING_SELLER = "An error occurred while deleting the seller.";
    String ERROR_RETRIEVING_SELLER = "An error occurred while retrieving the seller.";
    String ERROR_RETRIEVING_SELLERS = "An error occurred while retrieving sellers.";
    String ERROR_RETRIEVING_SELLER_COUNT = "An error occurred while retrieving seller count.";
    String ERROR_SEARCHING_SELLERS = "An error occurred while searching for sellers.";
    String ERROR_SEARCHING_SELLER_COUNT = "An error occurred while retrieving search seller count.";
    // --- Delivery Messages ---
    String DELIVERY_CREATED_SUCCESS = "Delivery created successfully.";
    String DELIVERY_RETRIEVED_SUCCESS = "Delivery retrieved successfully.";
    String DELIVERY_STATUS_UPDATED_SUCCESS = "Delivery status updated successfully.";
    String DELIVERY_CANCELED_SUCCESS = "Delivery canceled successfully."; // NEW
    String DELIVERIES_RETRIEVED_SUCCESS = "Deliveries retrieved successfully."; // NEW (for lists)
    String DELIVERY_COUNT_RETRIEVED_SUCCESS = "Delivery count retrieved successfully."; // NEW
    String DELIVERY_DELETED_SUCCESS = "Delivery deleted successfully."; // NEW

    // --- Delivery Error Messages ---
    String INVALID_DELIVERY_CREATION_REQUEST = "Invalid delivery creation request: Order ID, recipient name, address, delivery agent, and estimated delivery date are required.";
    String INVALID_ORDER_ID = "Invalid order ID provided.";
    String INVALID_TRACKING_NUMBER = "Invalid tracking number provided.";
    String INVALID_DELIVERY_STATUS_UPDATE_REQUEST = "Invalid delivery status update request: Tracking number and new status are required.";
    String INVALID_DELIVERY_STATUS = "Invalid delivery status: "; // Append status
    String DELIVERY_NOT_FOUND_FOR_ORDER = "Delivery not found for order ID: "; // Append orderId
    String DELIVERY_NOT_FOUND_FOR_TRACKING = "Delivery not found for tracking number: "; // Append trackingNumber
    String DELIVERY_NOT_FOUND_FOR_UPDATE = "Delivery not found for update with tracking number: "; // Append trackingNumber
    String DELIVERY_NOT_FOUND_FOR_CANCEL = "Delivery not found for cancellation with tracking number: "; // NEW
    String DELIVERY_NOT_FOUND_FOR_DELETE = "Delivery not found for deletion with tracking number: "; // NEW
    String DELIVERY_ALREADY_EXISTS_FOR_ORDER = "A delivery already exists for order ID: "; // Append orderId
    String INVALID_DELIVERY_STATUS_TRANSITION_FROM_DELIVERED = "Cannot change status from DELIVERED to any other status.";
    String INVALID_DELIVERY_STATUS_TRANSITION_FROM_CANCELED = "Cannot change status from CANCELED to any other status.";
    String INVALID_DELIVERY_STATUS_FOR_CANCELLATION = "Delivery cannot be canceled in current status: "; // NEW
    String INVALID_DELIVERY_AGENT = "Invalid delivery agent provided."; // NEW

    String ERROR_CREATING_DELIVERY = "An error occurred while creating the delivery.";
    String ERROR_RETRIEVING_DELIVERY_BY_ORDER = "An error occurred while retrieving delivery by order ID.";
    String ERROR_RETRIEVING_DELIVERY_BY_TRACKING = "An error occurred while retrieving delivery by tracking number.";
    String ERROR_UPDATING_DELIVERY_STATUS = "An error occurred while updating the delivery status.";
    String ERROR_CANCELING_DELIVERY = "An error occurred while canceling the delivery."; // NEW
    String ERROR_RETRIEVING_DELIVERIES_BY_AGENT = "An error occurred while retrieving deliveries by agent."; // NEW
    String ERROR_COUNTING_DELIVERIES_BY_AGENT = "An error occurred while counting deliveries by agent."; // NEW
    String ERROR_SEARCHING_DELIVERIES = "An error occurred while searching for deliveries."; // NEW
    String ERROR_COUNTING_SEARCH_DELIVERIES = "An error occurred while counting search results for deliveries."; // NEW
    String ERROR_RETRIEVING_ALL_DELIVERIES = "An error occurred while retrieving all deliveries."; // NEW
    String ERROR_COUNTING_ALL_DELIVERIES = "An error occurred while counting all deliveries."; // NEW
    String ERROR_DELETING_DELIVERY = "An error occurred while deleting the delivery."; // NEW

    // --- Media Messages ---
    String MEDIA_UPLOAD_SUCCESS = "Media uploaded successfully.";
    String MEDIA_RETRIEVED_SUCCESS = "Media asset retrieved successfully.";
    String MEDIA_ASSETS_RETRIEVED_SUCCESS = "Media assets retrieved successfully.";
    String MEDIA_COUNT_RETRIEVED_SUCCESS = "Media asset count retrieved successfully.";
    String MEDIA_DELETED_SUCCESS = "Media asset deleted successfully.";

    // --- Media Error Messages ---
    String INVALID_MEDIA_UPLOAD_REQUEST = "Invalid media upload request: assetName, fileContent, fileType, entityId, and entityType are required.";
    String INVALID_UNIQUE_FILE_NAME = "Invalid unique file name provided.";
    String INVALID_ENTITY_IDENTIFIERS = "Invalid entity ID or entity type provided.";
    String MEDIA_NOT_FOUND = "Media asset not found with unique file name: "; // Append uniqueFileName
    String MEDIA_NOT_FOUND_FOR_DELETE = "Media asset not found for deletion with unique file name: "; // Append uniqueFileName
    String UNSUPPORTED_FILE_TYPE = "Unsupported file type: "; // Append fileType
    String INVALID_BASE64_CONTENT = "Invalid Base64 file content provided.";

    String ERROR_UPLOADING_MEDIA = "An error occurred while uploading media.";
    String ERROR_RETRIEVING_MEDIA = "An error occurred while retrieving the media asset.";
    String ERROR_RETRIEVING_ENTITY_MEDIA = "An error occurred while retrieving media assets for the entity.";
    String ERROR_COUNTING_ENTITY_MEDIA = "An error occurred while counting media assets for the entity.";
    String ERROR_DELETING_MEDIA = "An error occurred while deleting the media asset.";
    String ERROR_RETRIEVING_ALL_MEDIA = "An error occurred while retrieving all media assets."; // NEW
    String ERROR_COUNTING_ALL_MEDIA = "An error occurred while counting all media assets."; // NEW

    // General messages 
    String INVALID_PAGINATION_PARAMETERS = "Invalid pagination parameters: offset must be non-negative, limit must be positive.";
    String INVALID_SEARCH_TERM = "Invalid search term provided."; // Re-using if generic search added
    
    // --- Error Messages (General) ---
    String MISSING_NEW_STATUS_BAD_REQUEST = "Missing or empty 'newStatus' in request body.";
    String INVALID_ORDER_STATUS_VALUE = "Invalid OrderStatus value: "; // Append value
    String ERROR_DURING_ORDER_CREATION = "An error occurred during order creation.";
    String ERROR_UPDATING_ORDER_STATUS = "An error occurred updating order status.";
    String ERROR_RETRIEVING_ORDERS = "An error occurred retrieving orders.";
    String ERROR_DELETING_ORDER = "An error occurred deleting order.";
    String GENERAL_SERVER_ERROR = "An unexpected server error occurred.";
    // --- Payment Messages ---
    String PAYMENT_INITIATED_SUCCESS = "Payment initiated successfully. Awaiting gateway confirmation.";
    String PAYMENT_CALLBACK_PROCESSED_SUCCESS = "Payment callback processed successfully.";
    String PAYMENT_STATUS_FETCHED_SUCCESS = "Payment status fetched successfully.";
    String PAYMENT_NOT_FOUND_FOR_ORDER = "Payment details not found for order ID: "; // Append ID
    String PAYMENT_NOT_FOUND = "Payment not found with transaction reference: "; // Append ID

    // --- Payment Error Messages ---
    String INVALID_PAYMENT_INITIATION_REQUEST = "Invalid payment initiation request: orderId and positive amount are required.";
    String INVALID_WEBHOOK_CALLBACK_REQUEST = "Invalid webhook callback request: transactionRef, status, and orderId are required.";
    String INVALID_PAYMENT_STATUS_VALUE = "Invalid payment status value: "; // Append value
    String MISSING_ORDER_ID_FOR_PAYMENT_STATUS = "Missing or empty orderId for payment status request.";
    String ERROR_INITIATING_PAYMENT = "An error occurred during payment initiation.";
    String ERROR_PROCESSING_CALLBACK = "An error occurred while processing payment callback.";
    String ERROR_FETCHING_PAYMENT_STATUS = "An error occurred while fetching payment status.";

    // --- User Management Messages ---
    String USER_DETAILS_RETRIEVED_SUCCESS = "User details retrieved successfully.";
    String USER_DETAILS_UPDATED_SUCCESS = "User details updated successfully.";
    String USER_DELETED_SUCCESS = "User account deleted successfully.";
    String USERS_RETRIEVED_SUCCESS = "All users retrieved successfully.";
    String ERROR_RETRIEVING_USER_DETAILS = "An error occurred while retrieving user details.";
    String ERROR_UPDATING_USER_DETAILS = "An error occurred while updating user details.";
    String ERROR_DELETING_USER = "An error occurred while deleting user account.";
    String ERROR_RETRIEVING_ALL_USERS = "An error occurred while retrieving all users.";
    
    // --- Authentication/Authorization Messages ---
    String UNAUTHENTICATED_USER = "User is not authenticated.";
    String INVALID_USER_ID_FORMAT = "Authenticated principal is not a valid user ID format.";
    String INVALID_USER_ID = "Authenticated principal is not a valid user ID.";
    String SECURITY_CONTEXT_NOT_FOUND = "Security context not found.";
    String USERNAME_ALREADY_TAKEN = "Error: Username is already taken!";
    String EMAIL_ALREADY_IN_USE = "Error: Email is already in use!";
    String USER_REGISTERED_SUCCESS = "User registered successfully!";
    String ROLE_USER_NOT_FOUND = "Error: Default role 'ROLE_USER' is not found in the database. Please ensure it's configured.";
    String ROLE_NOT_FOUND = "Error: Role is not found in the database: "; // Append role name
    String INVALID_ROLE_PROVIDED = "Error: Invalid role provided: "; // Append role name
    String ERROR_REGISTERING_USER = "An error occurred during user registration.";
    
    // Potentially for sign-in:
    String LOGIN_SUCCESS = "Login successful!";
    String INVALID_CREDENTIALS = "Invalid username or password.";
    String ERROR_AUTHENTICATING_USER = "An error occurred during authentication.";

    // --- Cart Messages ---
    String CART_ITEM_ADDED_SUCCESS = "Product added/updated in cart successfully.";
    String CART_RETRIEVED_SUCCESS = "Cart retrieved successfully.";
    String CART_ITEM_UPDATED_SUCCESS = "Cart item quantity updated successfully.";
    String CART_ITEM_REMOVED_SUCCESS = "Product removed from cart successfully.";
    String CART_CLEARED_SUCCESS = "Cart cleared successfully.";
    String CART_NOT_FOUND_FOR_USER = "Cart not found for user."; // Could be for empty cart too
    String PRODUCT_NOT_FOUND_IN_CART = "Product not found in cart: "; // Append ID

    // --- Cart Error Messages ---
    String INVALID_CART_ADD_REQUEST = "Invalid request: productId and positive quantity are required.";
    String INVALID_CART_UPDATE_REQUEST = "Invalid request: productId and non-negative quantity are required.";
    String INVALID_CART_REMOVE_REQUEST = "Invalid request: productId is required.";
    String ERROR_ADDING_CART_ITEM = "An error occurred while adding item to cart.";
    String ERROR_RETRIEVING_CART = "An error occurred while retrieving the cart.";
    String ERROR_UPDATING_CART_ITEM = "An error occurred while updating cart item.";
    String ERROR_REMOVING_CART_ITEM = "An error occurred while removing cart item.";
    String ERROR_CLEARING_CART = "An error occurred while clearing the cart.";
    
    // --- User Messages ---
    String USER_RETRIEVED_SUCCESS = "User retrieved successfully.";
    String USER_UPDATED_SUCCESS = "User updated successfully.";
    String CURRENT_USER_RETRIEVED_SUCCESS = "Current user details retrieved successfully.";

    // --- User Error Messages ---
    String ERROR_RETRIEVING_USERS = "An error occurred while retrieving users.";
    String ERROR_RETRIEVING_USER = "An error occurred while retrieving user.";
    String ERROR_UPDATING_USER = "An error occurred while updating user.";
    String INVALID_USER_UPDATE_REQUEST = "Invalid user update request: request body cannot be empty.";
    String USER_NOT_FOUND_AUTH_CONTEXT = "User not found in authentication context."; // Specific for /me endpoint    
    
}