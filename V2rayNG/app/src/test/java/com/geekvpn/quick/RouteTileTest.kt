package com.geekvpn.quick

import com.geekvpn.connection.RouteMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTileTest {
    @Test
    fun the_tile_alternates_smart_and_global() {
        assertEquals(RouteMode.Global, RouteTile.next(RouteMode.Smart))
        assertEquals(RouteMode.Smart, RouteTile.next(RouteMode.Global))
    }

    @Test
    fun direct_goes_back_to_smart() {
        assertEquals(RouteMode.Smart, RouteTile.next(RouteMode.Direct))
    }
}
