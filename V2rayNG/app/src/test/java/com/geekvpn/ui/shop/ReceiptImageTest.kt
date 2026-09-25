package com.geekvpn.ui.shop

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptImageTest {

    @Test
    fun a_large_photo_is_sampled_down_but_not_below_the_limit() {
        assertEquals(2, ReceiptImage.sampleSize(4000, 3000, 1600))
        assertEquals(1, ReceiptImage.sampleSize(3000, 2000, 1600))
        assertEquals(4, ReceiptImage.sampleSize(3000, 8000, 1600))
    }

    @Test
    fun the_long_side_is_fitted_and_the_shape_kept() {
        assertEquals(1600 to 1200, ReceiptImage.fitted(2000, 1500, 1600))
        assertEquals(900 to 1600, ReceiptImage.fitted(1800, 3200, 1600))
        assertEquals(800 to 600, ReceiptImage.fitted(800, 600, 1600))
    }

    @Test
    fun a_card_number_is_grouped_by_four() {
        assertEquals("6037 9911 2233 4455", groupCardNumber("6037991122334455"))
        assertEquals("6037-9911", groupCardNumber("6037-9911"))
    }
}
