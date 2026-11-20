package com.sarjdefteri.app.billing

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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.sarjdefteri.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Google Play Faturalandırma: tek seferlik "Şarj Defteri Pro" satın alımı.
 * Ürün kimliği Play Console'da [PRO_PRODUCT_ID] olarak (uygulama içi ürün, tüketilemez) tanımlanmalıdır.
 * Satın alma durumu DataStore'da önbelleğe alınır; uygulama çevrimdışıyken de Pro açık kalır.
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) : PurchasesUpdatedListener {

    enum class State { CONNECTING, READY, UNAVAILABLE }

    val state = MutableStateFlow(State.CONNECTING)
    val price = MutableStateFlow<String?>(null)
    val lastError = MutableStateFlow<String?>(null)
    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) { scope.launch { refresh() }; return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    state.value = State.READY
                    scope.launch { refresh() }
                } else {
                    state.value = State.UNAVAILABLE
                    lastError.value = result.debugMessage.ifEmpty { "Google Play faturalandırma kullanılamıyor" }
                }
            }

            override fun onBillingServiceDisconnected() { state.value = State.CONNECTING }
        })
    }

    private suspend fun refresh() {
        val productParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRO_PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
            )
        ).build()
        val pd = client.queryProductDetails(productParams)
        details = pd.productDetailsList?.firstOrNull()
        price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice

        val owned = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        // Yalnızca sorgu başarılıysa durumu güncelle (ağ hatasında Pro'yu geri alma).
        if (owned.billingResult.responseCode == BillingClient.BillingResponseCode.OK) handle(owned.purchasesList)
    }

    /** @return false ise ürün bilgisi henüz yok (Play bağlantısı/ürün tanımı eksik). */
    fun launchPurchase(activity: Activity): Boolean {
        val d = details ?: run { start(); return false }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
        ).build()
        val r = client.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) lastError.value = r.debugMessage
        return r.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun restore() = scope.launch { if (client.isReady) refresh() else start() }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { handle(purchases.orEmpty()) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> lastError.value = result.debugMessage.ifEmpty { "Satın alma tamamlanamadı (${result.responseCode})" }
        }
    }

    private suspend fun handle(purchases: List<Purchase>) {
        val pro = purchases.filter { PRO_PRODUCT_ID in it.products }
        pro.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }.forEach {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build())
        }
        settings.setProPurchased(pro.any { it.purchaseState == Purchase.PurchaseState.PURCHASED })
    }

    companion object {
        const val PRO_PRODUCT_ID = "sarj_defteri_pro"
    }
}
