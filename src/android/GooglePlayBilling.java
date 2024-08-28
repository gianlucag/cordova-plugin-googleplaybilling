package com.gg.gpbp;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClient.FeatureType;
import com.android.billingclient.api.BillingClient.ProductType;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetails.OneTimePurchaseOfferDetails;
import com.android.billingclient.api.ProductDetails.PricingPhase;
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.android.billingclient.api.QueryPurchasesParams;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public final class GooglePlayBilling
    extends CordovaPlugin
    implements PurchasesUpdatedListener,
    ProductDetailsResponseListener,
    AcknowledgePurchaseResponseListener,
    BillingClientStateListener,
    PurchasesResponseListener {

    private BillingClient billingClient;
    private CallbackContext callbackContext;
    private CallbackContext callbackPurchaseEvent;
    private HashMap<String, ProductDetails> currentProducts = new HashMap<String, ProductDetails>();

    private void log(String msg) {
        Log.d("BILLING", msg);
    }

    private JSONObject toJSON(Purchase p) throws JSONException {
        JSONObject ret = new JSONObject()
            .put("productIds", new JSONArray(p.getProducts()))
            .put("orderId", p.getOrderId())
            .put("getPurchaseState", p.getPurchaseState())
            .put("developerPayload", p.getDeveloperPayload())
            .put("acknowledged", p.isAcknowledged())
            .put("autoRenewing", p.isAutoRenewing())
            .put("accountId", p.getAccountIdentifiers().getObfuscatedAccountId())
            .put("profileId", p.getAccountIdentifiers().getObfuscatedProfileId())
            .put("signature", p.getSignature())
            .put("receipt", p.getOriginalJson().toString());
            return ret;
    }

    private JSONObject toJSON(ProductDetails p) throws JSONException {
        JSONObject ret = new JSONObject();

        ret.put("sku", p.getProductId());
        ret.put("type", p.getProductType());

        if (p.getProductType().equals(ProductType.INAPP)) {
            OneTimePurchaseOfferDetails details = p.getOneTimePurchaseOfferDetails();
            ret.put("price", details.getFormattedPrice());
        } else if (p.getProductType().equals(ProductType.SUBS)) {
            List<SubscriptionOfferDetails> subscriptionOfferDetails = p.getSubscriptionOfferDetails();
            SubscriptionOfferDetails details = subscriptionOfferDetails.get(0);
            JSONArray prices = new JSONArray();
            for (PricingPhase pricing : details.getPricingPhases().getPricingPhaseList()) {
                prices.put(pricing.getFormattedPrice());
            }
            ret.put("prices", prices);
        }

        return ret;
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) {
        log("execute()");
        log("action=" + action);

        try {
            if ("init".equals(action)) {
                this.callbackContext = callbackContext;
                init();
            }
            else if ("setPurchaseEventCallback".equals(action)) {
                this.callbackPurchaseEvent = callbackContext;
            }
            else if ("connect".equals(action)) {
                this.callbackContext = callbackContext;
                connect();
            }
            else if ("getInappPurchases".equals(action)) {
                this.callbackContext = callbackContext;
                getPurchases(true);
            }
            else if ("getSubsPurchases".equals(action)) {
                this.callbackContext = callbackContext;
                getPurchases(false);
            }
            else if ("buy".equals(action)) {
                this.callbackContext = callbackContext;
                String sku = args.getString(0);
                boolean isInapp = args.getBoolean(1);
                buy(sku, isInapp);
            }
            else if ("getInappProducts".equals(action)) {
                this.callbackContext = callbackContext;
                JSONArray productIdsJson = args.getJSONArray(0);
                List<String> skus = new ArrayList<String>();
                for (int i = 0; i < productIdsJson.length(); i++) {
                    skus.add(productIdsJson.getString(i));
                }
                getProducts(skus, true);
            }
            else if ("getSubsProducts".equals(action)) {
                this.callbackContext = callbackContext;
                JSONArray productIdsJson = args.getJSONArray(0);
                List<String> skus = new ArrayList<String>();
                for (int i = 0; i < productIdsJson.length(); i++) {
                    skus.add(productIdsJson.getString(i));
                }
                getProducts(skus, false);
            } else {
                return false;
            }
        } catch (JSONException e) {
            log("Exception=" + e.getMessage());
            return false;
        }

        return true;
    }

    public void init() {
        log("init()");

        if(billingClient == null) {
            billingClient = BillingClient.newBuilder(cordova.getActivity()).enablePendingPurchases().setListener(this).build();
        }

        callbackContext.success();
    }

    @Override
    public void onAcknowledgePurchaseResponse(BillingResult res) {
        log("onAcknowledgePurchaseResponse()");
        log("BillingResult=" + res.getResponseCode());
    }

    private void ackPurchase(Purchase purchase) {
        log("ackPurchase()");
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            log("Purchase state is PURCHASED");
            if (!purchase.isAcknowledged()) {
                log("Purchase is not acknowledged, acknowledging...");
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, this);
            }
        }
    }

    private void sendPurchaseEventResponse(int code) {
        final PluginResult result = new PluginResult(PluginResult.Status.OK, code);
        result.setKeepCallback(true);
        callbackPurchaseEvent.sendPluginResult(result);
    }

    @Override
    public void onPurchasesUpdated(final BillingResult res, final List<Purchase> purchases) {
        log("onPurchasesUpdated()");
        log("onPurchasesUpdated() - BillingResult=" + res.getResponseCode());
        
        if (billingClient == null) { // onPurchasesUpdated() is invoked by Google, make sure the client is initialized.
            log("onPurchasesUpdated() - Billing client not initialized, aborting...");
            return;
        }; 

        if(purchases != null) {
            log("onPurchasesUpdated() - List<Purchase>.size()=" + purchases.size());
            for (Purchase p : purchases) {
                ackPurchase(p);
            }
        }

        sendPurchaseEventResponse(res.getResponseCode());
    }

    private boolean isConnected() {
        return billingClient.isReady();
    } 

    @Override
    public void onBillingSetupFinished(final BillingResult res) {
        log("onBillingSetupFinished()");
        log("BillingResult=" + res.getResponseCode());

        if (res.getResponseCode() == BillingResponseCode.OK) {
            callbackContext.success(res.getResponseCode());
        }
        else {
            callbackContext.error(res.getResponseCode());
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        log("onBillingServiceDisconnected()");
    }

    public void connect() {
        log("connect()");
        billingClient.startConnection(this);
    }      

    @Override
    public void onProductDetailsResponse(BillingResult res, List<ProductDetails> products) {
        log("onProductDetailsResponse()");
        log("BillingResult=" + res.getResponseCode());
        log("List<ProductDetails>.size()=" + products.size());

        if (res.getResponseCode() != BillingResponseCode.OK) {
            callbackContext.error(res.getResponseCode());
            return;
        }
        
        if (products == null) {
            callbackContext.error(res.getResponseCode());
            return;
        }

        for (ProductDetails product : products) {
            currentProducts.put(product.getProductId(), product);
        }

        JSONArray response = new JSONArray();
        try {
            for (ProductDetails product : products) {
                response.put(toJSON(product));
            }
        } catch(Exception e) {
            callbackContext.error(res.getResponseCode());
            return;
        }

        callbackContext.success(response);
    }

    public void getProducts(List<String> skus, Boolean isInapp) {
        log("getProducts()");
        log("List<String>.size()=" + skus.size());
        log("Boolean=" + isInapp);

        List<Product> products = new ArrayList<Product>();
        for (int i = 0; i < skus.size(); i++) {
            log("Building product=" + skus.get(i));
            products.add(Product.newBuilder()
                .setProductId(skus.get(i))
                .setProductType(isInapp ? ProductType.INAPP : ProductType.SUBS)
                .build());
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(products).build();
        billingClient.queryProductDetailsAsync(params, this);
    }

    @Override
    public void onQueryPurchasesResponse(BillingResult res, List<Purchase> purchases) {
        log("onQueryPurchasesResponse()");
        log("BillingResult=" + res.getResponseCode());
        log("List<Purchase>.size()=" + purchases.size());

        if (res.getResponseCode() != BillingResponseCode.OK) {
            callbackContext.error(res.getResponseCode());
            return;
        }
        
        if (purchases == null) {
            callbackContext.error(res.getResponseCode());
            return;
        }

        for (Purchase p : purchases) {                
            ackPurchase(p);
        }
 
        JSONArray response = new JSONArray();
        try {
            for (Purchase p : purchases) {
                response.put(toJSON(p));
            }
        } catch(Exception e) {
            callbackContext.error(res.getResponseCode());
            return;
        }


        callbackContext.success(response);
    }

    public void getPurchases(Boolean isInapp) {
        log("getPurchases()");
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(isInapp ? ProductType.INAPP : ProductType.SUBS).build();
        billingClient.queryPurchasesAsync(params, this);
    }

    private void buy(String productId, Boolean isInapp) {
        log("buy()");
        log("productId=" + productId);
        log("isInapp=" + isInapp);

        ProductDetails productDetails = currentProducts.get(productId);
        List<ProductDetailsParams> productDetailsParamsList = new ArrayList<ProductDetailsParams>();

        if(isInapp) {
            productDetailsParamsList.add(ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build());
        } else {
            List<SubscriptionOfferDetails> subscriptionOfferDetails = productDetails.getSubscriptionOfferDetails();
            String offerToken = subscriptionOfferDetails.get(0).getOfferToken();
            productDetailsParamsList.add(ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build());  
        }

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build();

        cordova.setActivityResultCallback(this);
        BillingResult res = billingClient.launchBillingFlow(cordova.getActivity(), billingFlowParams);

        if (res.getResponseCode() != BillingResponseCode.OK) {
            sendPurchaseEventResponse(res.getResponseCode());
        }
    }
}










