package com.hive.hiragana.presentation.theme

import androidx.compose.ui.graphics.Color

// Brand — light scheme
val Vermilion = Color(0xFFBC3F34)
val VermilionContainer = Color(0xFFFFDAD4)
val OnVermilionContainer = Color(0xFF410001)
val IndigoSecondary = Color(0xFF5B5F97)
val WashiBackground = Color(0xFFFBF8F4)

// Canvas ("paper")
val PaperWhite = Color(0xFFFFFFFF)
val InkBlack = Color(0xFF20222E)
val GhostGrey = Color(0xFFD9D9E0)
val GridBorder = Color(0xFF8D99A6)
val GridGuide = Color(0xFFC7CFD8)
val DiagramStroke = Color(0xFF6B7280)

// Brand — dark ("sumi / night-washi") scheme: the same vermilion identity
// lifted to M3 dark tones (primary ≈ tone 80 of Vermilion), over warm
// brown-grey darks — deliberately not pure black. See Theme.kt DarkColors.
val VermilionGlow = Color(0xFFFFB4A8)
val OnVermilionGlow = Color(0xFF690003)
val VermilionContainerDark = Color(0xFF8E2A20)
val IndigoSecondaryDark = Color(0xFFC3C3EB)
val OnIndigoSecondaryDark = Color(0xFF2C2F66)
val IndigoContainerDark = Color(0xFF43467E)
val OnIndigoContainerDark = Color(0xFFE0E0FF)
val SumiBackground = Color(0xFF16130E)
val SumiOnBackground = Color(0xFFEAE1D4)
val SumiSurfaceVariant = Color(0xFF3D362D)
val SumiOnSurfaceVariant = Color(0xFFCFC6B8)
val SumiOutline = Color(0xFF988F81)

// Canvas ("paper") — dark appearance (see KanaColors.kt). The paper sits a
// step LIGHTER than SumiBackground so the sheet still reads as a card.
val NightPaper = Color(0xFF211D17)
val InkChalk = Color(0xFFEFE7DA)
val NightGhost = Color(0xFF4A453C)
val NightGridBorder = Color(0xFF6E675B)
val NightGridGuide = Color(0xFF3A352C)
val NightDiagramStroke = Color(0xFFA8AEB8)

// Grade buttons
val GradeEasyContainer = Color(0xFFDCEDC8)
val GradeEasyContent = Color(0xFF1B5E20)
val GradeOkayContainer = Color(0xFFFFECB3)
val GradeOkayContent = Color(0xFF7A4F01)
val GradeHardContainer = Color(0xFFFFCDD2)
val GradeHardContent = Color(0xFF8E1616)

// Grade.AGAIN — a distinct red-ish pair, one step more alarming than Hard's
// pink (deeper container, near-maroon content) so the four grade buttons
// read as a worst-to-best ramp: Again -> Hard -> Good -> Easy.
val GradeAgainContainer = Color(0xFFF6B4B4)
val GradeAgainContent = Color(0xFF5C0A0A)

// Answer-feedback semantics (see FeedbackColors.kt). "Correct!" is green (not
// the vermilion primary, which read like a mistake); a miss is a SOFT AMBER in
// low-stakes practice (the graded quiz keeps error-red). Each has a dark twin
// so the light-on-dark card grounds stay legible.
val FeedbackGreen = Color(0xFF2E7D32)
val FeedbackGreenDark = Color(0xFF7FD48A)
val FeedbackAmber = Color(0xFFB26A00)
val FeedbackAmberDark = Color(0xFFF0B357)

// The "How to remember" reveal chip: a calm teal (help/info), replacing the
// vermilion primaryContainer so a correct reveal no longer looks like an error.
val HintTealContainer = Color(0xFFCFE8E9)
val OnHintTealContainer = Color(0xFF13494B)
val HintTealContainerDark = Color(0xFF204A4C)
val OnHintTealContainerDark = Color(0xFFA7DADC)
