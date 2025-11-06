# cordova-plugin-googleplaybilling

Google Play In-App Purchases and Subscriptions for Cordova

## Summary

This Cordova plugin enables Google Play In-App purchases and Subscriptions within an Android Cordova app. It utilizes Google Play Billing library version 7.0.

## Prerequisites

In your Google Play Console, create the In-App and/or Subscription products for your Android app. You must have at least one product (In-App or Subscription) to use the plugin.

## Install the plugin into your Cordova project

### Step 1

Download the latest version of the plugin from this [link](https://github.com/gianlucag/cordova-plugin-googleplaybilling/archive/refs/tags/8.0.0.zip) or from the GitHub "Code" -> "Download ZIP" button.

### Step 2

Unzip the plugin inside the main folder of your Cordova project. Rename the plugin folder to `cordova-plugin-googleplaybilling`.

### Step 3

Add the plugin to your Cordova project by running the following command:

```sh
cordova plugin add cordova-plugin-googleplaybilling
```

## Using the plugin

Using the plugin is straightforward. The plugin provides methods to initialize, purchase products, retrieve localized prices, and check product ownership.

### Initialize the plugin

At the earliest possible moment in your app, initialize the plugin by calling the `init()` method.

```javascript
GooglePlayBilling.init(options);
```

Below is the options object to pass to the init function:

```javascript
var options = {
    products: products,
    enableCache: true,
    onInitSuccess: onInitSuccess,
    onInitFail: onInitFail,
    onPurchaseSuccess: onPurchaseSuccess
    onPurchaseFail: onPurchaseFail,
};
```

#### `products`

This is the list of products you want to make available for purchase. These products must already be created on the Google Play Console. Here's an example:

```javascript
var products = [
	{
		sku: "product_id_example",
		type: "inapp", // this is an in-app non consumable product
	},
	{
		sku: "product_id_example2",
		type: "subs", // this is a subscription product
	},
];
```

Properties of the product object:

- `sku` - The product id of the item, a string.
- `type` - The type of the product
  - `"inapp"` - In-App non consumable
  - `"subs"` - Subscription

#### `enableCache`

Enables the plugin's internal cache for purchases. If the Google Play library is temporarily unavailable, the user will still have access to purchased products.

#### `onInitSuccess`

This callback is invoked after the plugin has been successfully initialized. Your app should transition to the main view after this callback is executed. All other plugin methods should be called only after this callback has been triggered by the plugin.

#### `onInitFail`

This callback is triggered if the plugin fails to initialize. After this callback is executed, your app should transition to the main view, but you must inform the user that any paid features will not be available. Without proper initialization of the plugin, it is not possible to verify if a product is owned.

#### `onPurchaseSuccess`

This callback is triggered when a purchase is successful. Use it to unlock the features tied to the purchased product.

#### `onPurchaseFail`

This callback is triggered when a purchase fails. An error code specifying the reason for the failure is provided.

#### Example

```javascript
var products = [
	{
		sku: "product_id_example",
		type: "inapp",
	},
	{
		sku: "product_id_example2",
		type: "subs",
	},
];

var options = {
	products: products,
	enableCache: true,
	onInitSuccess: () => {
		AfterInit(); // Init successful. Go to the main view of the app.
	},
	onInitFail: () => {
		// init failed. Go to the main view of the app but inform the user!
		AfterInitNoBilling();
	},
	onPurchaseSuccess: () => {
		showBuyCompletePopup(); // show my purchase completed popup
	},
	onPurchaseFail: (errorCode) => {
		if (errorCode == "USER_CANCELED") {
			// user canceled the purchase
		} else if (errorCode == "ITEM_ALREADY_OWNED") {
			// product is already owned
		} else if (errorCode == "UNABLE_TO_CHARGE") {
			// payment method was refused by Google
		} else if (errorCode == "NETWORK") {
			// network error
		} else {
			// should not enter here
		}
	},
};

GooglePlayBilling.init(options);
```

### Buy a product

To initiate a purchase from your app, call the plugin's `buy()` method, passing in the productId of the item you want to purchase. The plugin will then display the standard Google Play purchase screen, allowing the user to select a payment method and complete the purchase.

#### Example

```javascript
GooglePlayBilling.buy("product_id_example"); // launch the puchase screen
```

Upon a successful purchase, the plugin will trigger the `onPurchaseSuccess` callback. If the purchase fails, the `onPurchaseFail` callback will be triggered.

You are responsible for implementing the `onPurchaseSuccess` callback (as defined in the `init()` method) to correctly unlock paid features in your app and to provide feedback to the user about the successful purchase (e.g., showing a popup message).

The `onPurchaseFail` callback provides an error code describing the reason for a failed purchase attempt.  
Below are all possible values and the recommended handling strategies.

---

###### `USER_CANCELED`

The user dismissed or canceled the Google Play purchase screen.  
This is not an error condition — your app should **silently handle** this case (e.g., simply close the purchase UI).

**Suggested action:**  
No popup message required. Optionally, allow the user to retry later.

---

###### `BILLING_UNAVAILABLE`

Unable to charge the user — the payment method is invalid or unavailable.  
This can occur if the Google Play billing service is temporarily disabled or not supported on the device.

**Suggested action:**  
Inform the user that billing is unavailable and suggest retrying later or checking their payment methods.

---

###### `DEVELOPER_ERROR`

Incorrect usage of the Google Play Billing API.  
This usually indicates a bug in your integration or an invalid request sent to the billing system.

**Suggested action:**  
Log the error for developer review. Do not show a user-facing message.

---

###### `ERROR`

A transient Google Play error occurred.  
Retrying the purchase operation after a short delay might fix the issue.

**Suggested action:**  
Display a generic “Temporary error, please try again” message.

---

###### `FEATURE_NOT_SUPPORTED`

A feature requested by the plugin during the purchase process is not supported by the current version of Google Play on the device.

**Suggested action:**  
Inform the user that this feature is not available on their device.

---

###### `ITEM_ALREADY_OWNED`

The user attempted to purchase an item they already own.  
This should not normally occur, since your app should check ownership using the `isOwned()` method before initiating a purchase.

**Suggested action:**  
Disable the "Buy" button for items already owned. If it occurs, notify the user that the item is already purchased.

---

###### `ITEM_NOT_OWNED`

The item is not owned by the user.  
This can occur due to a Google Play synchronization issue.

**Suggested action:**  
Prompt the user to restore purchases or retry synchronization.

---

###### `ITEM_UNAVAILABLE`

The item is not available for purchase.  
This could mean that the product ID is invalid or not published correctly in the Play Console.

**Suggested action:**  
Display an error message indicating the item is unavailable.

---

###### `NETWORK_ERROR`

A transient network error occurred while communicating with Google Play services.

**Suggested action:**  
Notify the user of the network issue and suggest checking their connection before retrying.

---

###### `SERVICE_DISCONNECTED`

The plugin lost connection with the local Google Play service.  
Reinitializing the billing plugin and retrying the operation may resolve the issue.

**Suggested action:**  
Retry initialization before allowing another purchase attempt.

---

###### `SERVICE_TIMEOUT`

Deprecated by the Google Play API in favor of `SERVICE_UNAVAILABLE`.  
The action took longer than expected and should no longer occur on updated systems.

**Suggested action:**  
Treat as a generic service error. Retry after a delay.

---

###### `SERVICE_UNAVAILABLE`

The Google Play API is temporarily unavailable.  
Retrying later might fix the issue.

**Suggested action:**  
Show a popup message indicating that the service is temporarily unavailable.

---

###### `NETWORK`

A generic network error occurred during the transaction.

**Suggested action:**  
Notify the user with a popup message indicating a connection issue.

---

###### `UNKNOWN`

The Google Play API returned an unknown error code. This can happen due to edge cases, new error codes from the billing service, or unexpected internal failures.

**Suggested action:**  
Show a generic error message to the user. Optionally provide a "Report issue" or "Retry" action.

---

#### Example

Here's an example implementation of the `onPurchaseFail` callback:

```javascript
onPurchaseFail: (errorCode) => {
	if (errorCode === "USER_CANCELED") {
		// do nothing
	} else if (errorCode === "ITEM_ALREADY_OWNED") {
		showErrorItemAlreadyOwnedPopup();
	} else if (errorCode === "BILLING_UNAVAILABLE") {
		showErrorUnableToChargePopup();
	} else if (errorCode === "NETWORK_ERROR") {
		showErrorNetworkPopup();
	} else {
		showGenericPurchaseErrorPopup();
	}
};
```

Your app should respond appropriately based on the error code provided by the `onPurchaseFail` callback.

### Get the price of an In-App product

Google localizes product prices based on language and currency. You can retrieve the localized price string by calling the `getPrice()` method.

#### Example

Get the localized price of the In-App product with id `product_id_example`

```javascript
const price = GooglePlayBilling.getPrice("product_id_example");
console.log(price); // "3.99 €"
```

This returns the localized price of the product, including its currency symbol.

### Get the Price of a Subscription Product

For subscription products, the `getPrice()` method returns an array of prices, with each item representing the price for a specific subscription period. For instance, if a subscription includes a free trial, the `getPrice()` method will return an array with two elements: the first will be the string `"Free"`, and the second will be the actual subscription price.

#### Example

Retrieve the localized price of the subscription product with the ID `product_id_example2`.

```javascript
const prices = GooglePlayBilling.getPrice("product_id_example2");
console.log(prices); // ["Free", "3.99 €"]
```

The string "Free" is localized by Google based on the user language.

### Check if a product is owned

Your app should determine which paid features to unlock based on already purchased products. To verify if a product is owned by the user, call the `isOwned()` method.

#### Example

Check if product with id `product_id_example` is owned:

```javascript
const isOwned = GooglePlayBilling.isOwned("product_id_example");
console.log(isOwned); // true or false
```

This returns a boolean value: `true` if the product is owned, `false` if it is not.

### Get the Google receipt of a purchased product

For every purchased product, Google generates a digital receipt that is useful for local or server validation. Use the `getPurchase()` method to obtain a purchase object. The purchase object contains the Google receipt, the signature, and the orderId.

#### Example

Get the receipt of the purchased product `product_id_example`:

```javascript
const purchase = GooglePlayBilling.getPurchase("product_id_example");
```

Here's an example of a purchase object returned by the `getPurchase()` method:

```javascript
{
    "signature": "HL1KbvGoz********",
    "receipt": "{\"orderId\":\"GPA.xxxx-xxxx-xxxx-xxxx\",\"packageName\":\"com.my.package\",\"productId\":\"product_id_example\",\"purchaseTime\":1546516701653,\"purchaseState\":0,\"purchaseToken\":\"deakbabjf*******\"}",
    "orderId": "GPA.xxxx-xxxx-xxxx-xxxx"
}
```

Of course, the `getPurchase()` method should only be called for already owned product. Otherwise the method returns `null`.

## Best Practices

To ensure the correct usage of the plugin, follow these best practices:

### Initialization

Initialize the plugin as early as possible in the app's lifecycle. No other methods will be available until the plugin has been successfully initialized.

### Unlocking Paid Features

The app should determine which paid products the user owns and unlock the corresponding features. Use the `isOwned()` method to check if a product is owned.

Example:

```javascript
if (GooglePlayBilling.isOwned("inapp_geolocation")) {
	UI.GeolocationButton.Enable();
} else {
	UI.GeolocationButton.Disable();
}
```

There are three key moments in the app's lifecycle when these checks should be performed:

#### At startup

When the user first opens the app, immediately after the plugin is successfully initialized, check which paid products are owned. This should be done before displaying the UI to the user.

#### On the onResume Event

Since some payment methods are completed outside the device, it’s important to check for owned products not only at startup but also during the app's `onResume()` event. If the user doesn’t close the app, completes a purchase, and then resumes the app, the paid features should be available.

#### Immediately After a Successful Purchase

Unlock paid features as soon as the `onPurchaseSuccess()` callback is triggered.
