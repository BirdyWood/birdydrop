package io.github.birdywood.birdydrop.utils

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Fonctions utilitaires pour éclaircir/assombrir (peuvent être dans ce fichier ou un autre fichier d'utilitaires)
fun Color.lighter(factor: Float = 0.25f): Color {
    val red = (this.red * (1 + factor)).coerceIn(0f, 1f)
    val green = (this.green * (1 + factor)).coerceIn(0f, 1f)
    val blue = (this.blue * (1 + factor)).coerceIn(0f, 1f)
    return Color(red, green, blue, this.alpha)
}

fun Color.darker(factor: Float = 0.15f): Color {
    val red = (this.red * (1 - factor)).coerceIn(0f, 1f)
    val green = (this.green * (1 - factor)).coerceIn(0f, 1f)
    val blue = (this.blue * (1 - factor)).coerceIn(0f, 1f)
    return Color(red, green, blue, this.alpha)
}

class ColorGenerator {
    companion object {
        // Vos couleurs de base
        private val Corail = Color(0xFFFF7F50)
        private val Menthe = Color(0xFF98FB98)
        private val Lavande = Color(0xFFB19CD9)
        private val Peche = Color(0xFFFFDAB9)
        private val BleuCiel = Color(0xFF87CEEB)
        private val RoseBonbon = Color(0xFFFFB6C1)
        private val JauneCitron = Color(0xFFFFFFE0)
        private val VertDEau = Color(0xFFAFEEEE)

        // Palette de couleurs unies (peut toujours être utile)
        val palettePastelVivesUnies = listOf(
            Corail, Menthe, Lavande, Peche, BleuCiel, RoseBonbon, JauneCitron, VertDEau
        )

        // --- Palette de Dégradés ---
        // Chaque dégradé est créé à partir d'une couleur de base et d'une variation.
        // Vous pouvez choisir le type de dégradé (linear, vertical, horizontal)
        // et les couleurs de début/fin.
        val paletteDeGradients = listOf(
            Brush.linearGradient(colors = listOf(Corail, Corail.lighter())),
            Brush.verticalGradient(colors = listOf(Menthe.darker(), Menthe)),
            Brush.horizontalGradient(colors = listOf(Lavande, Lavande.lighter(0.4f))),
            Brush.linearGradient(colors = listOf(Peche.darker(0.1f), Peche.lighter(0.2f))),
            Brush.verticalGradient(colors = listOf(BleuCiel, VertDEau)), // Dégradé entre deux couleurs de la palette
            Brush.horizontalGradient(colors = listOf(RoseBonbon.darker(), RoseBonbon.lighter(0.3f))),
            Brush.linearGradient(colors = listOf(JauneCitron, JauneCitron.darker(0.05f))), // Jaune est déjà très clair
            Brush.verticalGradient(colors = listOf(VertDEau.lighter(), BleuCiel)) // Autre exemple entre deux couleurs
        )
        // Assurez-vous que paletteDeGradients a le même nombre d'éléments que palettePastelVivesUnies
        // si vous voulez une correspondance 1 pour 1 via l'index, ou gérez la taille différemment.

        /**
         * Retourne une couleur unie de la palette de manière déterministe pour un nom.
         */
        fun getColorForName(name: String, colorPalette: List<Color> = palettePastelVivesUnies): Color {
            if (colorPalette.isEmpty()) {
                return Color.Black // Couleur par défaut
            }
            val hashCode = abs(name.hashCode())
            val index = hashCode % colorPalette.size
            return colorPalette[index]
        }

        /**
         * Retourne un Brush (dégradé) de la palette de dégradés de manière déterministe pour un nom.
         * Pour un même nom, le dégradé retourné sera toujours le même.
         *
         * @param name Le nom (String) pour lequel attribuer un dégradé.
         * @param gradientPalette La liste de Brushes parmi lesquels choisir. Par défaut, utilise paletteDeGradients.
         * @return Un Brush de la palette.
         */
        fun getGradientForName(name: String, gradientPalette: List<Brush> = paletteDeGradients): Brush {
            if (gradientPalette.isEmpty()) {
                // Gérer le cas où la palette de dégradés est vide.
                // Retourner un dégradé par défaut simple.
                return Brush.linearGradient(listOf(Color.LightGray, Color.Gray))
            }

            val hashCode = abs(name.hashCode())
            val index = hashCode % gradientPalette.size
            return gradientPalette[index]
        }

        /**
         * Variante: Génère un dégradé dynamiquement basé sur la couleur unie obtenue pour le nom.
         * Cela évite de prédéfinir une liste de dégradés si vous voulez juste une variation simple.
         */
        fun generateDynamicGradientForName(name: String, colorPalette: List<Color> = palettePastelVivesUnies): Brush {
            val baseColor = getColorForName(name, colorPalette)
            // Créez un type de dégradé par défaut, par exemple linéaire avec une version plus claire.
            // Vous pouvez rendre cela plus configurable si nécessaire.
            return Brush.linearGradient(colors = listOf(baseColor, baseColor.lighter(0.3f)))
        }
    }
}