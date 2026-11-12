package com.stormpanda.megingiard.mirrorserver;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MirrorServerTest {

    @Test
    public void selectBackendKind_usesSurfaceControlOnApi33_andDisplayManagerOnApi34Plus() {
        assertEquals(
                SurfaceControlReflect.BackendKind.SURFACE_CONTROL_LEGACY,
                SurfaceControlReflect.selectBackendKind(33));
        assertEquals(
                SurfaceControlReflect.BackendKind.DISPLAY_MANAGER_MIRROR,
                SurfaceControlReflect.selectBackendKind(34));
        assertEquals(
                SurfaceControlReflect.BackendKind.DISPLAY_MANAGER_MIRROR,
                SurfaceControlReflect.selectBackendKind(35));
        assertEquals(
                SurfaceControlReflect.BackendKind.DISPLAY_MANAGER_MIRROR,
                SurfaceControlReflect.selectBackendKind(36));
    }

    @Test
    public void requireNonNullVirtualDisplay_failsClosedOnNull_andReturnsNonNull() {
        Object expected = new Object();
        assertSame(
                expected,
                SurfaceControlReflect.requireNonNullVirtualDisplay(expected, "megingiard-direct-0"));

        try {
            SurfaceControlReflect.requireNonNullVirtualDisplay(null, "megingiard-direct-0");
            fail("Expected IllegalStateException when VirtualDisplay is null");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("megingiard-direct-0"));
        }
    }

    @Test
    public void sessionCoordinator_replacesActiveSessionInOrder_sessionClosedBeforeSurfaceReleased() {
        List<String> events = new ArrayList<>();
        DirectMirrorServer.SessionCoordinator<FakeSurfaceHandle> coordinator =
                new DirectMirrorServer.SessionCoordinator<>(
                        (name, width, height, displayId, surface) -> {
                            events.add("open:" + surface.id + ":" + width + "x" + height + ":d" + displayId);
                            return () -> events.add("closeSession:" + surface.id);
                        });

        FakeSurfaceHandle first = new FakeSurfaceHandle("s1", true, events);
        FakeSurfaceHandle second = new FakeSurfaceHandle("s2", true, events);

        assertTrue(coordinator.applySurface(first, 1920, 1080));
        assertTrue(coordinator.applySurface(second, 1920, 1080));
        coordinator.release();
        coordinator.release(); // idempotent

        assertEquals(
                List.of(
                        "open:s1:1920x1080:d0",
                        "closeSession:s1",
                        "releaseSurface:s1",
                        "open:s2:1920x1080:d0",
                        "closeSession:s2",
                        "releaseSurface:s2"),
                events);
    }

    @Test
    public void sessionCoordinator_releasesIncomingSurfaceOnInvalidArgsOrOpenerFailure() {
        List<String> events = new ArrayList<>();
        DirectMirrorServer.SessionCoordinator<FakeSurfaceHandle> failingCoordinator =
                new DirectMirrorServer.SessionCoordinator<>(
                        (name, width, height, displayId, surface) -> {
                            throw new IllegalStateException("DisplayManager returned null");
                        });

        FakeSurfaceHandle failedOpenSurface = new FakeSurfaceHandle("failOpen", true, events);
        assertFalse(failingCoordinator.applySurface(failedOpenSurface, 1920, 1080));
        assertEquals(List.of("releaseSurface:failOpen"), events);

        events.clear();
        FakeSurfaceHandle invalidDimsSurface = new FakeSurfaceHandle("badDims", true, events);
        assertFalse(failingCoordinator.applySurface(invalidDimsSurface, 0, 1080));
        assertEquals(List.of("releaseSurface:badDims"), events);

        events.clear();
        FakeSurfaceHandle invalidSurface = new FakeSurfaceHandle("invalid", false, events);
        assertFalse(failingCoordinator.applySurface(invalidSurface, 1920, 1080));
        assertEquals(List.of("releaseSurface:invalid"), events);
    }

    private static final class FakeSurfaceHandle implements DirectMirrorServer.SurfaceHandle {
        final String id;
        private final boolean valid;
        private final List<String> events;

        FakeSurfaceHandle(String id, boolean valid, List<String> events) {
            this.id = id;
            this.valid = valid;
            this.events = events;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void release() {
            events.add("releaseSurface:" + id);
        }
    }
}
