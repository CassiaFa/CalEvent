package fr.calevent.core.crop

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CropDragTest {
    private val rect = NormalizedRect(0.2f, 0.3f, 0.8f, 0.7f)
    private val w = 1000f
    private val h = 1000f
    private val touch = 48f
    private val minSize = 0.15f

    private fun hit(x: Float, y: Float) = CropDrag.hitTest(rect, x, y, w, h, touch)
    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 1e-4f, "expected $expected got $actual")

    @Test
    fun `corners are grabbed within the touch radius`() {
        assertEquals(CropHandle.TopLeft, hit(200f, 300f))
        assertEquals(CropHandle.TopLeft, hit(170f, 330f))
        assertEquals(CropHandle.BottomRight, hit(820f, 690f))
        assertEquals(CropHandle.TopRight, hit(800f, 300f))
        assertEquals(CropHandle.BottomLeft, hit(190f, 710f))
    }

    @Test
    fun `edges are grabbed anywhere along them`() {
        assertEquals(CropHandle.Top, hit(500f, 310f))
        assertEquals(CropHandle.Bottom, hit(500f, 720f))
        assertEquals(CropHandle.Left, hit(180f, 500f))
        assertEquals(CropHandle.Right, hit(830f, 500f))
    }

    @Test
    fun `inside moves the frame and outside grabs nothing`() {
        assertEquals(CropHandle.Move, hit(500f, 500f))
        assertNull(hit(50f, 50f))
        assertNull(hit(500f, 900f))
    }

    @Test
    fun `nearest handle wins when areas overlap`() {
        val small = NormalizedRect(0.4f, 0.4f, 0.46f, 0.46f)
        assertEquals(CropHandle.TopLeft, CropDrag.hitTest(small, 405f, 405f, w, h, touch))
        assertEquals(CropHandle.BottomRight, CropDrag.hitTest(small, 455f, 455f, w, h, touch))
    }

    @Test
    fun `corner drag moves two sides`() {
        val r = CropDrag.drag(rect, CropHandle.TopLeft, 0.1f, -0.1f, minSize, minSize)
        near(0.3f, r.left); near(0.2f, r.top); near(0.8f, r.right); near(0.7f, r.bottom)
    }

    @Test
    fun `edge drag moves one side`() {
        val r = CropDrag.drag(rect, CropHandle.Right, 0.1f, 0.5f, minSize, minSize)
        near(0.9f, r.right); near(0.2f, r.left); near(0.3f, r.top); near(0.7f, r.bottom)
    }

    @Test
    fun `drag is clamped to the image`() {
        val r = CropDrag.drag(rect, CropHandle.BottomRight, 0.9f, 0.9f, minSize, minSize)
        near(1f, r.right); near(1f, r.bottom)
        val l = CropDrag.drag(rect, CropHandle.Left, -0.9f, 0f, minSize, minSize)
        near(0f, l.left)
    }

    @Test
    fun `minimum size is kept`() {
        val r = CropDrag.drag(rect, CropHandle.Left, 0.9f, 0f, minSize, minSize)
        near(0.8f - minSize, r.left)
        val b = CropDrag.drag(rect, CropHandle.Bottom, 0f, -0.9f, minSize, 0.2f)
        near(0.5f, b.bottom)
    }

    @Test
    fun `move keeps the size and stops at the borders`() {
        val r = CropDrag.drag(rect, CropHandle.Move, 0.5f, -0.5f, minSize, minSize)
        near(0.6f, r.right - r.left); near(0.4f, r.bottom - r.top)
        near(0.4f, r.left); near(1f, r.right); near(0f, r.top); near(0.4f, r.bottom)
    }

    @Test
    fun `minimum size scales with the screen`() {
        near(0.15f, CropDrag.minimumSize(1000f, 96f))
        near(0.3f, CropDrag.minimumSize(320f, 96f))
        near(0.5f, CropDrag.minimumSize(100f, 96f))
        near(0.15f, CropDrag.minimumSize(0f, 96f))
    }

    @Test
    fun `focus point follows the grabbed handle`() {
        val tl = CropDrag.focusPoint(rect, CropHandle.TopLeft)
        near(0.2f, tl.first); near(0.3f, tl.second)
        val bottom = CropDrag.focusPoint(rect, CropHandle.Bottom)
        near(0.5f, bottom.first); near(0.7f, bottom.second)
        val right = CropDrag.focusPoint(rect, CropHandle.Right)
        near(0.8f, right.first); near(0.5f, right.second)
    }
}
