package cl.reabill.monitor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object PremiumShapes {
    val CardLarge = RoundedCornerShape(28.dp)
    val CardMedium = RoundedCornerShape(20.dp)
    val Button = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    val material = Shapes(
        extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
    )
}
