package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView
import dev.rebelonion.translator.Language

enum class Languages(
    @field:StringRes override val textId: Int
): TextView {

    System( R.string.language_system_default ),
    Afrikaans( R.string.language_name_afrikaans ),
    Arabic( R.string.language_name_arabic ),
    Azerbaijani( R.string.language_name_azerbaijani ),
    Bashkir( R.string.language_name_bashkir ),
    Bengali( R.string.language_name_bengali ),
    Catalan( R.string.language_name_catalan ),
    Danish( R.string.language_name_danish ),
    English( R.string.language_name_english ),
    Esperanto( R.string.language_name_esperanto ),
    Estonian( R.string.language_name_estonian ),
    ChineseSimplified( R.string.language_name_chinese_simplified ),
    ChineseTraditional( R.string.language_name_chinese_traditional ),
    Czech( R.string.language_name_czech ),
    Dutch( R.string.language_name_dutch ),
    Filipino( R.string.language_name_filipino ),
    Finnish( R.string.language_name_finnish ),
    French( R.string.language_name_french ),
    Galician( R.string.language_name_galician ),
    German( R.string.language_name_german ),
    Greek( R.string.language_name_greek ),
    Hebrew( R.string.language_name_hebrew ),
    Hindi( R.string.language_name_hindi ),
    Hungarian( R.string.language_name_hungarian ),
    Italian( R.string.language_name_italian ),
    Indonesian( R.string.language_name_indonesian ),
    Interlingua( R.string.language_name_interlingua ),
    Irish( R.string.language_name_irish ),
    Japanese( R.string.language_name_japanese ),
    Korean( R.string.language_name_korean ),
    Malayalam( R.string.language_name_malayalam ),
    Norwegian( R.string.language_name_norwegian ),
    Odia( R.string.language_name_odia ),
    Persian( R.string.language_name_persian ),
    Polish( R.string.language_name_polish ),
    PortugueseBrazilian( R.string.language_name_portuguese_brazilian ),
    Portuguese( R.string.language_name_portuguese ),
    Romanian( R.string.language_name_romanian ),
    Russian( R.string.language_name_russian ),
    SerbianCyrillic( R.string.language_name_serbian_cyrillic ),
    SerbianLatin( R.string.language_name_serbian_latin ),
    Sinhala( R.string.language_name_sinhala ),
    Spanish( R.string.language_name_spanish ),
    Swedish( R.string.language_name_swedish ),
    Tamil( R.string.language_name_tamil ),
    Telugu( R.string.language_name_telugu ),
    Turkish( R.string.language_name_turkish ),
    Ukrainian( R.string.language_name_ukrainian ),
    Vietnamese( R.string.language_name_vietnamese );

    val code: String
        get() = when (this) {
            System -> "system"
            Afrikaans -> "af"
            Azerbaijani -> "az"
            Arabic -> "ar"
            Bashkir -> "ba"
            Bengali -> "bn"
            Catalan -> "ca"
            ChineseSimplified -> "zh-CN"
            ChineseTraditional -> "zh-TW"
            Danish -> "da"
            Dutch -> "nl"
            English -> "en"
            Esperanto -> "eo"
            Estonian -> "et"
            Filipino -> "fil"
            Finnish -> "fi"
            Galician -> "gl"
            Italian -> "it"
            Indonesian -> "in"
            Irish -> "ga"
            Japanese -> "ja"
            Korean -> "ko"
            Czech -> "cs"
            German -> "de"
            Greek -> "el"
            Hebrew -> "iw" 
            Hindi -> "hi"
            Hungarian -> "hu"
            Interlingua -> "ia"
            Spanish -> "es"
            French -> "fr"
            Malayalam -> "ml"
            Norwegian -> "no"
            Odia -> "or"
            Persian -> "fa"
            Polish -> "pl"
            Portuguese -> "pt"
            PortugueseBrazilian -> "pt-BR"
            Romanian -> "ro"
            Russian -> "ru"
            SerbianCyrillic -> "sr"
            SerbianLatin -> "sr-CS"
            Sinhala -> "si"
            Swedish -> "sv"
            Tamil -> "ta"
            Telugu -> "te"
            Turkish -> "tr"
            Ukrainian -> "uk"
            Vietnamese -> "vi"
        }
        
    val translatorLanguage: Language
        get() = when (this) {
            System -> Language.AUTO
            Afrikaans -> Language.AFRIKAANS
            Arabic -> Language.ARABIC
            Azerbaijani -> Language.AZERBAIJANI
            Bashkir -> Language.BASQUE
            Bengali -> Language.BENGALI
            Catalan -> Language.CATALAN
            ChineseSimplified -> Language.CHINESE_SIMPLIFIED
            ChineseTraditional -> Language.CHINESE_TRADITIONAL
            Czech -> Language.CZECH
            Danish -> Language.DANISH
            Dutch -> Language.DUTCH
            English -> Language.ENGLISH
            Esperanto -> Language.ESPERANTO
            Estonian -> Language.ESTONIAN
            Filipino -> Language.FILIPINO
            Finnish -> Language.FINNISH
            French -> Language.FRENCH
            Galician -> Language.GALICIAN
            German -> Language.GERMAN
            Greek -> Language.GREEK
            Hebrew -> Language.HEBREW_HE
            Hindi -> Language.HINDI
            Hungarian -> Language.HUNGARIAN
            Indonesian -> Language.INDONESIAN
            Interlingua -> Language.LATIN
            Irish -> Language.IRISH
            Japanese -> Language.JAPANESE
            Korean -> Language.KOREAN
            Italian -> Language.ITALIAN
            Malayalam -> Language.MALAYALAM
            Norwegian -> Language.NORWEGIAN
            Odia -> Language.ODIA
            Persian -> Language.PERSIAN
            Polish -> Language.POLISH
            PortugueseBrazilian -> Language.PORTUGUESE
            Portuguese -> Language.PORTUGUESE
            Romanian -> Language.ROMANIAN
            Russian -> Language.RUSSIAN
            SerbianCyrillic, SerbianLatin -> Language.SERBIAN
            Sinhala -> Language.SINHALA
            Spanish -> Language.SPANISH
            Swedish -> Language.SWEDISH
            Tamil -> Language.TAMIL
            Telugu -> Language.TELUGU
            Turkish -> Language.TURKISH
            Ukrainian -> Language.UKRAINIAN
            Vietnamese -> Language.VIETNAMESE
        }
}

enum class Countries {
    ZZ,
    AR,
    DZ,
    AU,
    AT,
    AZ,
    BH,
    BD,
    BY,
    BE,
    BO,
    BA,
    BR,
    BG,
    KH,
    CA,
    CL,
    HK,
    CO,
    CR,
    HR,
    CY,
    CZ,
    DK,
    DO,
    EC,
    EG,
    SV,
    EE,
    FI,
    FR,
    GE,
    DE,
    GH,
    GR,
    GT,
    HN,
    HU,
    IS,
    IN,
    ID,
    IQ,
    IE,
    IL,
    IT,
    JM,
    JP,
    JO,
    KZ,
    KE,
    KR,
    KW,
    LA,
    LV,
    LB,
    LY,
    LI,
    LT,
    LU,
    MK,
    MY,
    MT,
    MX,
    ME,
    MA,
    NP,
    NL,
    NZ,
    NI,
    NG,
    NO,
    OM,
    PK,
    PA,
    PG,
    PY,
    PE,
    PH,
    PL,
    PT,
    PR,
    QA,
    RO,
    RU,
    SA,
    SN,
    RS,
    SG,
    SK,
    SI,
    ZA,
    ES,
    LK,
    SE,
    CH,
    TW,
    TZ,
    TH,
    TN,
    TR,
    UG,
    UA,
    AE,
    GB,
    US,
    UY,
    VE,
    VN,
    YE,
    ZW;

    val countryName: String
        get() = when (this) {
            ZZ -> "Global"
            AR -> "Argentina"
            DZ -> "Algeria"
            AU -> "Australia"
            AT -> "Austria"
            AZ -> "Azerbaijan"
            BH -> "Bahrain"
            BD -> "Bangladesh"
            BY -> "Belarus"
            BE -> "Belgium"
            BO -> "Bolivia"
            BA -> "Bosnia and Herzegovina"
            BR -> "Brazil"
            BG -> "Bulgaria"
            KH -> "Cambodia"
            CA -> "Canada"
            CL -> "Chile"
            HK -> "Hong Kong"
            CO -> "Colombia"
            CR -> "Costa Rica"
            HR -> "Croatia"
            CY -> "Cyprus"
            CZ -> "Czech Republic"
            DK -> "Denmark"
            DO -> "Dominican Republic"
            EC -> "Ecuador"
            EG -> "Egypt"
            SV -> "El Salvador"
            EE -> "Estonia"
            FI -> "Finland"
            FR -> "France"
            GE -> "Georgia"
            DE -> "Germany"
            GH -> "Ghana"
            GR -> "Greece"
            GT -> "Guatemala"
            HN -> "Honduras"
            HU -> "Hungary"
            IS -> "Iceland"
            IN -> "India"
            ID -> "Indonesia"
            IQ -> "Iraq"
            IE -> "Ireland"
            IL -> "Israel"
            IT -> "Italy"
            JM -> "Jamaica"
            JP -> "Japan"
            JO -> "Jordan"
            KZ -> "Kazakhstan"
            KE -> "Kenya"
            KR -> "South Korea"
            KW -> "Kuwait"
            LA -> "Lao"
            LV -> "Latvia"
            LB -> "Lebanon"
            LY -> "Libya"
            LI -> "Liechtenstein"
            LT -> "Lithuania"
            LU -> "Luxembourg"
            MK -> "Macedonia"
            MY -> "Malaysia"
            MT -> "Malta"
            MX -> "Mexico"
            ME -> "Montenegro"
            MA -> "Morocco"
            NP -> "Nepal"
            NL -> "Netherlands"
            NZ -> "New Zealand"
            NI -> "Nicaragua"
            NG -> "Nigeria"
            NO -> "Norway"
            OM -> "Oman"
            PK -> "Pakistan"
            PA -> "Panama"
            PG -> "Papua New Guinea"
            PY -> "Paraguay"
            PE -> "Peru"
            PH -> "Philippines"
            PL -> "Poland"
            PT -> "Portugal"
            PR -> "Puerto Rico"
            QA -> "Qatar"
            RO -> "Romania"
            RU -> "Russian Federation"
            SA -> "Saudi Arabia"
            SN -> "Senegal"
            RS -> "Serbia"
            SG -> "Singapore"
            SK -> "Slovakia"
            SI -> "Slovenia"
            ZA -> "South Africa"
            ES -> "Spain"
            LK -> "Sri Lanka"
            SE -> "Sweden"
            CH -> "Switzerland"
            TW -> "Taiwan"
            TZ -> "Tanzania"
            TH -> "Thailand"
            TN -> "Tunisia"
            TR -> "Turkey"
            UG -> "Uganda"
            UA -> "Ukraine"
            AE -> "United Arab Emirates"
            GB -> "United Kingdom"
            US -> "United States"
            UY -> "Uruguay"
            VE -> "Venezuela (Bolivarian Republic)"
            VN -> "Vietnam"
            YE -> "Yemen"
            ZW -> "Zimbabwe"
        }
}



