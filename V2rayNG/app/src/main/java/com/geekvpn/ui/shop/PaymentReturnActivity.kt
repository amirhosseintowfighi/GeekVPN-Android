package com.geekvpn.ui.shop

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.geekvpn.ui.home.HomeActivity

/**
 * `geekvpn://payment/result?payment=…&result=ok|pending|failed|unknown`, the
 * link the backend's gateway return page opens. Exported because a browser
 * opens it, so it trusts nothing in it: only a known result word is passed on,
 * and only as a hint for which message to show. The wallet and services are
 * re-read from the server either way.
 */
class PaymentReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = intent?.data?.getQueryParameter("result")?.takeIf { it in RESULTS }
            ?: ShopViewModel.RESULT_UNKNOWN
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(HomeActivity.EXTRA_PAYMENT_RESULT, result)
        )
        finish()
    }

    private companion object {
        val RESULTS = setOf(
            ShopViewModel.RESULT_OK,
            ShopViewModel.RESULT_PENDING,
            ShopViewModel.RESULT_FAILED,
            ShopViewModel.RESULT_UNKNOWN,
        )
    }
}
