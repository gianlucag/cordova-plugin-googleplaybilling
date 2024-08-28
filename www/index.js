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
        if(res == 0) options.onPurchaseSuccess();
        else if(res == 1) options.onPurchaseFail("USER_CANCELED");
        else if(res == 7) options.onPurchaseFail("ITEM_ALREADY_OWNED");
        else if(res == 3) options.onPurchaseFail("UNABLE_TO_CHARGE");
        else options.onPurchaseFail("NETWORK");
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

