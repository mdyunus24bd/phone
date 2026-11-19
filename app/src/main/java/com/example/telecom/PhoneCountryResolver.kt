package com.example.telecom

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Resolves the human-readable calling country name for any phone number.
 *
 * Resolution strategy:
 * 1. Matches international calling prefixes (`+` or `00`) using longest-prefix ITU-T E.164 lookup,
 *    including NANP area-code disambiguation for Canada vs. United States and major territories.
 * 2. Recognizes unprefixed international numbers (11–15 digits starting with a known country code).
 * 3. For local/national numbers without an international prefix, falls back to the device's
 *    cellular network ISO, SIM country ISO, or system locale country.
 */
object PhoneCountryResolver {

    // Major Canadian NANP area codes to distinguish Canada from United States for +1 numbers
    private val canadaAreaCodes = setOf(
        "204", "226", "236", "249", "250", "263", "289", "306", "343", "354", "365", "367", "368",
        "403", "416", "418", "431", "437", "438", "450", "468", "474", "506", "514", "519", "548",
        "579", "581", "584", "587", "604", "613", "639", "647", "672", "683", "705", "709", "742",
        "753", "778", "780", "782", "807", "819", "825", "867", "873", "879", "902", "905"
    )

    // ITU-T E.164 calling codes mapped to country names (checked longest prefix first: 4 -> 3 -> 2 -> 1)
    private val callingCodeToCountry: Map<String, String> = mapOf(
        // 4-digit NANP Caribbean & Atlantic codes
        "1242" to "Bahamas",
        "1246" to "Barbados",
        "1264" to "Anguilla",
        "1268" to "Antigua & Barbuda",
        "1284" to "British Virgin Islands",
        "1345" to "Cayman Islands",
        "1441" to "Bermuda",
        "1473" to "Grenada",
        "1649" to "Turks & Caicos",
        "1664" to "Montserrat",
        "1670" to "Northern Mariana Islands",
        "1671" to "Guam",
        "1684" to "American Samoa",
        "1721" to "Sint Maarten",
        "1758" to "Saint Lucia",
        "1767" to "Dominica",
        "1784" to "Saint Vincent",
        "1787" to "Puerto Rico",
        "1809" to "Dominican Republic",
        "1829" to "Dominican Republic",
        "1849" to "Dominican Republic",
        "1868" to "Trinidad & Tobago",
        "1869" to "Saint Kitts & Nevis",
        "1876" to "Jamaica",
        "1939" to "Puerto Rico",

        // 3-digit calling codes
        "212" to "Morocco",
        "213" to "Algeria",
        "216" to "Tunisia",
        "218" to "Libya",
        "220" to "Gambia",
        "221" to "Senegal",
        "222" to "Mauritania",
        "223" to "Mali",
        "224" to "Guinea",
        "225" to "Ivory Coast",
        "226" to "Burkina Faso",
        "227" to "Niger",
        "228" to "Togo",
        "229" to "Benin",
        "230" to "Mauritius",
        "231" to "Liberia",
        "232" to "Sierra Leone",
        "233" to "Ghana",
        "234" to "Nigeria",
        "235" to "Chad",
        "236" to "Central African Republic",
        "237" to "Cameroon",
        "238" to "Cape Verde",
        "240" to "Equatorial Guinea",
        "241" to "Gabon",
        "242" to "Congo",
        "243" to "DR Congo",
        "244" to "Angola",
        "248" to "Seychelles",
        "249" to "Sudan",
        "250" to "Rwanda",
        "251" to "Ethiopia",
        "252" to "Somalia",
        "253" to "Djibouti",
        "254" to "Kenya",
        "255" to "Tanzania",
        "256" to "Uganda",
        "257" to "Burundi",
        "258" to "Mozambique",
        "260" to "Zambia",
        "261" to "Madagascar",
        "263" to "Zimbabwe",
        "264" to "Namibia",
        "265" to "Malawi",
        "266" to "Lesotho",
        "267" to "Botswana",
        "268" to "Eswatini",
        "350" to "Gibraltar",
        "351" to "Portugal",
        "352" to "Luxembourg",
        "353" to "Ireland",
        "354" to "Iceland",
        "355" to "Albania",
        "356" to "Malta",
        "357" to "Cyprus",
        "358" to "Finland",
        "359" to "Bulgaria",
        "370" to "Lithuania",
        "371" to "Latvia",
        "372" to "Estonia",
        "373" to "Moldova",
        "374" to "Armenia",
        "375" to "Belarus",
        "376" to "Andorra",
        "377" to "Monaco",
        "380" to "Ukraine",
        "381" to "Serbia",
        "382" to "Montenegro",
        "383" to "Kosovo",
        "385" to "Croatia",
        "386" to "Slovenia",
        "387" to "Bosnia & Herzegovina",
        "389" to "North Macedonia",
        "420" to "Czech Republic",
        "421" to "Slovakia",
        "423" to "Liechtenstein",
        "501" to "Belize",
        "502" to "Guatemala",
        "503" to "El Salvador",
        "504" to "Honduras",
        "505" to "Nicaragua",
        "506" to "Costa Rica",
        "507" to "Panama",
        "509" to "Haiti",
        "591" to "Bolivia",
        "592" to "Guyana",
        "593" to "Ecuador",
        "595" to "Paraguay",
        "597" to "Suriname",
        "598" to "Uruguay",
        "670" to "Timor-Leste",
        "673" to "Brunei",
        "675" to "Papua New Guinea",
        "676" to "Tonga",
        "677" to "Solomon Islands",
        "678" to "Vanuatu",
        "679" to "Fiji",
        "685" to "Samoa",
        "850" to "North Korea",
        "852" to "Hong Kong",
        "853" to "Macau",
        "855" to "Cambodia",
        "856" to "Laos",
        "880" to "Bangladesh",
        "886" to "Taiwan",
        "960" to "Maldives",
        "961" to "Lebanon",
        "962" to "Jordan",
        "963" to "Syria",
        "964" to "Iraq",
        "965" to "Kuwait",
        "966" to "Saudi Arabia",
        "967" to "Yemen",
        "968" to "Oman",
        "970" to "Palestine",
        "971" to "United Arab Emirates",
        "972" to "Israel",
        "973" to "Bahrain",
        "974" to "Qatar",
        "975" to "Bhutan",
        "976" to "Mongolia",
        "977" to "Nepal",
        "992" to "Tajikistan",
        "993" to "Turkmenistan",
        "994" to "Azerbaijan",
        "995" to "Georgia",
        "996" to "Kyrgyzstan",
        "998" to "Uzbekistan",

        // 2-digit calling codes
        "20" to "Egypt",
        "27" to "South Africa",
        "30" to "Greece",
        "31" to "Netherlands",
        "32" to "Belgium",
        "33" to "France",
        "34" to "Spain",
        "36" to "Hungary",
        "39" to "Italy",
        "40" to "Romania",
        "41" to "Switzerland",
        "43" to "Austria",
        "44" to "United Kingdom",
        "45" to "Denmark",
        "46" to "Sweden",
        "47" to "Norway",
        "48" to "Poland",
        "49" to "Germany",
        "51" to "Peru",
        "52" to "Mexico",
        "53" to "Cuba",
        "54" to "Argentina",
        "55" to "Brazil",
        "56" to "Chile",
        "57" to "Colombia",
        "58" to "Venezuela",
        "60" to "Malaysia",
        "61" to "Australia",
        "62" to "Indonesia",
        "63" to "Philippines",
        "64" to "New Zealand",
        "65" to "Singapore",
        "66" to "Thailand",
        "81" to "Japan",
        "82" to "South Korea",
        "84" to "Vietnam",
        "86" to "China",
        "90" to "Turkey",
        "91" to "India",
        "92" to "Pakistan",
        "93" to "Afghanistan",
        "94" to "Sri Lanka",
        "95" to "Myanmar",
        "98" to "Iran",

        // 1-digit calling codes
        "1" to "United States",
        "7" to "Russia"
    )

    fun resolveCountryName(context: Context, rawPhoneNumber: String): String {
        val trimmed = rawPhoneNumber.trim()
        if (trimmed.isEmpty()) return ""

        val cleaned = trimmed.filter { it.isDigit() || it == '+' }
        if (cleaned.isEmpty()) return ""

        // 1. Explicit international format: +XXX... or 00XXX...
        val explicitInternationalDigits = when {
            cleaned.startsWith("+") -> cleaned.drop(1).filter { it.isDigit() }
            cleaned.startsWith("00") && cleaned.length >= 6 -> cleaned.drop(2).filter { it.isDigit() }
            else -> null
        }

        if (!explicitInternationalDigits.isNullOrEmpty()) {
            matchCallingCode(explicitInternationalDigits)?.let { return it }
        }

        // 2. Unprefixed international digits (e.g., 88017..., 9198..., 447..., 9715..., 1415...)
        val digitsOnly = cleaned.filter { it.isDigit() }
        if (digitsOnly.length in 11..15 && !digitsOnly.startsWith("0")) {
            matchCallingCode(digitsOnly)?.let { return it }
        }

        // 3. Fallback for national/local SIM numbers using Android Telephony ISO / device locale
        return resolveDeviceCountryName(context)
    }

    private fun matchCallingCode(digits: String): String? {
        if (digits.isEmpty()) return null

        // Special case: +7 Kazakhstan (76 / 77) vs Russia
        if (digits.startsWith("7") && digits.length >= 2) {
            val second = digits[1]
            if (second == '6' || second == '7') return "Kazakhstan"
        }

        // Check 4-digit, 3-digit, 2-digit prefixes first
        for (len in 4 downTo 2) {
            if (digits.length >= len) {
                val prefix = digits.substring(0, len)
                callingCodeToCountry[prefix]?.let { return it }
            }
        }

        // Check 1-digit prefixes (+1 NANP with Canada check, +7 Russia)
        if (digits.startsWith("1")) {
            if (digits.length >= 4 && digits.substring(1, 4) in canadaAreaCodes) {
                return "Canada"
            }
            return "United States"
        }
        if (digits.startsWith("7")) {
            return "Russia"
        }
        return null
    }

    private fun resolveDeviceCountryName(context: Context): String {
        val iso = try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val networkIso = tm?.networkCountryIso?.trim().orEmpty()
            val simIso = tm?.simCountryIso?.trim().orEmpty()
            when {
                networkIso.length == 2 -> networkIso
                simIso.length == 2 -> simIso
                else -> Locale.getDefault().country
            }
        } catch (_: Exception) {
            Locale.getDefault().country
        }

        if (iso.length != 2) return ""
        return Locale("", iso.uppercase(Locale.US)).getDisplayCountry(Locale.getDefault())
    }
}
