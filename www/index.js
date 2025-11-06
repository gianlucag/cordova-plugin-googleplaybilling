var cordova = require('cordova');

function callPlugin(functionName, params) {
    return new Promise((resolve, reject) => {
        cordova.exec((result) => {
            resolve(result);
        }, (error) => {
            reject(error);
        }, 'GooglePlayBilling', functionName, params);
    });
}

let products = {};
let enableCache = false;
let debugProduct = null;

function getProductSkusByType(productItems, isInapp) {
    return productItems
    .filter(item => item.type === isInapp ? "inapp" : "subs")
    .map(item => item.sku);
}

function findPurchase(sku, purchases) {
    for(var p = 0; p < purchases.length; p++) {
        if(purchases[p].productIds.includes(sku)) return {
            signature: purchases[p].signature,
            receipt: purchases[p].receipt,
            orderId: purchases[p].orderId
        };
    }
    return null;
}

function exists(sku) {
    if (products.hasOwnProperty(sku)) {
        return true;
    } else {
        return false;
    }
}

function buildCache() {
    window.localStorage.setItem("BILLING_cache", JSON.stringify(products));
}

function restoreCache() {
    var jsonCache = window.localStorage.getItem("BILLING_cache");
    if(jsonCache) {
        try {
            products = JSON.parse(jsonCache);
            return true;
        } catch(e) {
            return false;
        }
    } else {
        return false;
    }    
}

function isOwned(sku) {
    if(debugProduct) {
        return debugProduct.sku == sku ? true : false;
    } else {
        if(exists(sku)) {
            return products[sku].purchase ? true : false;
        } else {
            return false;
        }
    }
};

function getPrice(sku) {
    if(exists(sku)) {
        if(products[sku].type == "inapp") {
            return products[sku].price;
        } else {
            return products[sku].prices;
        }
    } else {
        return null;
    }
};

function getPurchase(sku) {
    if(debugProduct) {
        if(debugProduct.sku == sku) {
            return debugProduct.purchase;
        }   
    } else {
        if(exists(sku)) {
            return products[sku].purchase;
        } else {
            return null;
        }
    }
};

function buy(sku) {
    if(exists(sku)) {
        callPlugin("buy", [sku, products[sku].type == "inapp" ? true : false]);
    }
}

function setDebugOwnedProduct(product) {
    debugProduct = product;
}

function init(options) {

    enableCache = options.enableCache;
    products = {};

    function onPurchaseEvent(res) {
        switch (res) {
            case 0:
                options.onPurchaseSuccess();
                break;
            case 3:
                // Unable to charge, payment method not valid
                options.onPurchaseFail("BILLING_UNAVAILABLE");
                break;
            case 5:
                // Incorrect usage of the billing API
                options.onPurchaseFail("DEVELOPER_ERROR");
                break;
            case 6:
                // Transient Google Play error, retrying might fix the issue
                options.onPurchaseFail("ERROR");
                break;
            case -2:
                // A feature requested by the plugin during payment is not supported by Google Play
                options.onPurchaseFail("FEATURE_NOT_SUPPORTED");
                break;
            case 7:
                // The item is already owned by the user
                // This error should not occur, because the caller is expected to use the isOwned() method
                // to check whether the item is already owned before attempting to purchase it
                options.onPurchaseFail("ITEM_ALREADY_OWNED");
                break;
            case 8:
                // The item is not owned by the user. Usually a Google Play synchronization error 
                options.onPurchaseFail("ITEM_NOT_OWNED");
                break;
            case 4:
                // The item is not available for purchase.
                options.onPurchaseFail("ITEM_UNAVAILABLE");
                break;
            case 12:
                // A transient network error while calling the Google Play API
                options.onPurchaseFail("NETWORK_ERROR");
                break;
            case -1:
                // The plugin lost connection with the local Google Play service
                // Reinitializing the plugin and trying again might fix the issue
                options.onPurchaseFail("SERVICE_DISCONNECTED");
                break;
            case -3:
                // Deprecated by Google Play API in favor of SERVICE_UNAVAILABLE error
                // The action took longer than expected. On updated systems should never occur
                options.onPurchaseFail("SERVICE_TIMEOUT");
                break;
            case 2:
                // The Google Play API is momentarly unavailable. Retrying might fix the issue
                options.onPurchaseFail("SERVICE_UNAVAILABLE");
                break;
            case 1:
                // Transaction was canceled by the user
                options.onPurchaseFail("USER_CANCELED");
                break;
            default:
                options.onPurchaseFail("UNKNOWN");
                break;
        }
    };
    

    async function initialize() {
        try {
            cordova.exec(onPurchaseEvent, onPurchaseEvent, 'GooglePlayBilling', "setPurchaseEventCallback", []);
            await callPlugin("init", []);
            await callPlugin("connect", []);
            const inappProducts = await callPlugin("getInappProducts", [getProductSkusByType(options.products, true)]);
            const subsProducts = await callPlugin("getSubsProducts", [getProductSkusByType(options.products, false)]);
            const inappPurchases = await callPlugin("getInappPurchases", []);
            const subsPurchases = await callPlugin("getSubsPurchases", []);

            inappProducts.forEach((p) => {
                p.purchase = findPurchase(p.sku, inappPurchases);
                products[p.sku] = p;
            });
    
            subsProducts.forEach((p) => {
                p.purchase = findPurchase(p.sku, subsPurchases);
                products[p.sku] = p;
            });
    
            if(enableCache) buildCache();
            options.onInitSuccess();
        } catch (error) {
            if(enableCache && restoreCache()) {
                options.onInitSuccess();
            } else {
                options.onInitFail();
            }
        }
    };

    initialize();
}

module.exports = {
    setDebugOwnedProduct: setDebugOwnedProduct,
    getPurchase: getPurchase,
    getPrice: getPrice,
    isOwned: isOwned,
    init: init,
    buy: buy
};

