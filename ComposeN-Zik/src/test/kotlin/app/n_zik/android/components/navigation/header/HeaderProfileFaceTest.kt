package app.n_zik.android.components.navigation.header

import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.faceInitials
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HeaderProfileFaceTest {
    @Test
    fun `header begins with profile initials while resolved avatar loads`() {
        val face = initialHeaderProfileFace("N-Zik Fan")
        assertEquals("N-Zik Fan", face.name)
        assertEquals(FaceAvatar.Initials("N-Zik Fan"), face.avatar)
        assertEquals("NF", faceInitials((face.avatar as FaceAvatar.Initials).name))
    }
}
