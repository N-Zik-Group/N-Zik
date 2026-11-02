package app.n_zik.android.components.glass

import app.n_zik.android.components.glass.backdrop.Backdrop

/** A child-free rendered surface that retains its material instead of substituting its raw input. */
internal class RenderedMaterialBackdrop(source: Backdrop) : Backdrop by source
