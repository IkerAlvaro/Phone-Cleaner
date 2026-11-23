package com.ikeralvaro.phonecleaner.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.ikeralvaro.phonecleaner.BuildConfig
import com.ikeralvaro.phonecleaner.feature.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PurchaseMessage { NONE, UNAVAILABLE, ERROR }

/**
 * Compra única «Quitar anuncios» con Google Play Billing. El producto se crea en Play Console con el id
 * [BuildConfig.REMOVE_ADS_PRODUCT_ID]. Sin servidor propio, la compra se comprueba en el móvil al arrancar.
 */
class BillingManager(context: Context, private val settings: SettingsRepository) : PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val productId = BuildConfig.REMOVE_ADS_PRODUCT_ID

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var product: ProductDetails? = null
    private val connectedOnce = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _price = MutableStateFlow<String?>(null)
    /** Precio con formato de la tienda (p. ej. "2,99 €"), o null si aún no se conoce. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _message = MutableStateFlow(PurchaseMessage.NONE)
    val message: StateFlow<PurchaseMessage> = _message.asStateFlow()

    /** Conexión inicial, una sola vez por proceso (desde MainActivity). */
    fun connectOnce() {
        if (connectedOnce.compareAndSet(false, true)) connect()
    }

    fun connect() {
        if (client.isReady) {
            if (product == null) queryProduct()
            restorePurchases()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun launchPurchase(activity: Activity) {
        val details = product
        if (details == null || !client.isReady) {
            _message.value = PurchaseMessage.UNAVAILABLE
            // connect() vuelve a pedir el producto si aún no se conoce.
            connect()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.let(::setOfferToken) }
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) _message.value = PurchaseMessage.ERROR
    }

    fun consumeMessage() {
        _message.value = PurchaseMessage.NONE
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::handle)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restorePurchases()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _message.value = PurchaseMessage.ERROR
        }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val found = details.productDetailsList.firstOrNull { it.productId == productId }
            product = found
            _price.value = found?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                ?: found?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /** Recupera la compra (cambio de móvil, reinstalación) y la retira si Google la ha reembolsado. */
    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.filter {
                productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED && isSignatureValid(it)
            }
            owned.forEach(::handle)
            if (owned.isEmpty()) scope.launch { settings.setPremium(false) }
        }
    }

    private fun handle(purchase: Purchase) {
        if (productId !in purchase.products || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!isSignatureValid(purchase)) {
            _message.value = PurchaseMessage.ERROR
            return
        }
        scope.launch { settings.setPremium(true) }
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { }
        }
    }

    /** Firma local con la clave de [BillingConfig]; si no hay clave configurada no se comprueba. */
    private fun isSignatureValid(purchase: Purchase): Boolean =
        BillingConfig.verify(purchase.originalJson, purchase.signature)
}
