#include "namedays.h"

// Czech name days lookup table [month 1-12][day 1-31]
// Index 0 for both month and day is unused
static const char *namedays[13][32] = {
    // [0] unused
    {""},

    // [1] January
    {"",
     "Nový rok", "Karina", "Radmila", "Diana", "Dalimil",
     "Tři králové", "Vilma", "Čestmír", "Vladan", "Břetislav",
     "Bohdana", "Pravoslav", "Edita", "Radovan", "Alice",
     "Ctirad", "Drahoslav", "Vladislav", "Doubravka", "Ilona",
     "Běla", "Slavomír", "Zdeněk", "Milena", "Miloš",
     "Zora", "Ingrid", "Otýlie", "Zdislava", "Robin",
     "Marika"},

    // [2] February
    {"",
     "Hynek", "Nela", "Blažej", "Jarmila", "Dobromila",
     "Vanda", "Veronika", "Milada", "Apolena", "Mojmír",
     "Božena", "Slavěna", "Věnceslav", "Valentýn", "Jiřina",
     "Ljuba", "Miloslava", "Gizela", "Patrik", "Oldřich",
     "Lenka", "Petr", "Svatopluk", "Matěj", "Liliana",
     "Dorota", "Alexandr", "Lumír", "Horymír", "", ""},

    // [3] March
    {"",
     "Bedřich", "Anežka", "Kamil", "Stela", "Kazimír",
     "Miroslav", "Tomáš", "Gabriela", "Františka", "Viktorie",
     "Anděla", "Řehoř", "Růžena", "Rút/Matylda", "Ida",
     "Elena/Herbert", "Vlastimil", "Eduard", "Josef", "Světlana",
     "Radek", "Leona", "Ivona", "Gabriel", "Marián",
     "Emanuel", "Dita", "Soňa", "Taťána", "Arnošt",
     "Kvido"},

    // [4] April
    {"",
     "Hugo", "Erika", "Richard", "Ivana", "Miroslava",
     "Vendula", "Heřman/Hermína", "Ema", "Dušan", "Darja",
     "Izabela", "Julius", "Aleš", "Vincenc", "Anastázie",
     "Irena", "Rudolf", "Valérie", "Rostislav", "Marcela",
     "Alexandra", "Evženie", "Vojtěch", "Jiří", "Marek",
     "Oto", "Jaroslav", "Vlastislav", "Robert", "Blahoslav",
     ""},

    // [5] May
    {"",
     "Svátek práce", "Zikmund", "Alexej", "Květoslav", "Klaudie",
     "Radoslav", "Stanislav", "Den vítězství", "Ctibor", "Blažena",
     "Svatava", "Pankrác", "Servác", "Bonifác", "Žofie",
     "Přemysl", "Aneta", "Nataša", "Ivo", "Zbyšek",
     "Monika", "Emil", "Vladimír", "Jana", "Viola",
     "Filip", "Valdemar", "Vilém", "Maxmilián", "Ferdinand",
     "Kamila"},

    // [6] June
    {"",
     "Laura", "Jarmil", "Tamara", "Dalibor", "Dobroslav",
     "Norbert", "Iveta", "Medard", "Stanislav", "Gita",
     "Bruno", "Antonie", "Antonín", "Roland", "Vít",
     "Zbyněk", "Adolf", "Milan", "Leoš", "Květa",
     "Alois", "Pavla", "Zdeňka", "Jan", "Ivan",
     "Adriana", "Ladislav", "Lubomír", "Petr a Pavel", "Šárka",
     ""},

    // [7] July
    {"",
     "Jaroslava", "Patricie", "Radomír", "Prokop", "Cyril a Metoděj",
     "Den upálení M.J.Husa", "Bohuslava", "Nora", "Drahoslava", "Libuše/Amálie",
     "Olga", "Bořek", "Markéta", "Karolína", "Jindřich",
     "Luboš", "Martina", "Drahomíra", "Čeněk", "Ilja",
     "Vítězslav", "Magdaléna", "Libor", "Kristýna", "Jakub",
     "Anna", "Věroslav", "Viktor", "Marta", "Bořivoj",
     "Ignác"},

    // [8] August
    {"",
     "Oskar", "Gustav", "Miluše", "Dominik", "Kristián",
     "Oldřiška", "Lada", "Soběslav", "Roman", "Vavřinec",
     "Zuzana", "Klára", "Alena", "Alan", "Hana",
     "Jáchym", "Petra", "Helena", "Ludvík", "Bernard",
     "Johana", "Bohuslav", "Sandra", "Bartoloměj", "Radim",
     "Luděk", "Otakar", "Augustýn", "Evelína", "Vladěna",
     "Pavlína"},

    // [9] September
    {"",
     "Linda/Samuel", "Adéla", "Bronislav", "Jindřiška", "Boris",
     "Boleslav", "Regína", "Mariana", "Daniela", "Irma",
     "Denisa", "Marie", "Lubor", "Radka", "Jolana",
     "Ludmila", "Naděžda", "Kryštof", "Zita", "Oleg",
     "Matouš", "Darina", "Berta", "Jaromír", "Zlata",
     "Andrea", "Jonáš", "Václav", "Michal", "Jeroným",
     ""},

    // [10] October
    {"",
     "Igor", "Olivie/Oliver", "Bohumil", "František", "Eliška",
     "Hanuš", "Justýna", "Věra", "Štefan/Sára", "Marina",
     "Andrej", "Marcel", "Renáta", "Agáta", "Tereza",
     "Havel", "Hedvika", "Lukáš", "Michaela", "Vendelín",
     "Brigita", "Sabina", "Teodor", "Nina", "Beáta",
     "Erik", "Šarlota/Zoe", "Den vzniku ČSR", "Silvie", "Tadeáš",
     "Štěpánka"},

    // [11] November
    {"",
     "Felix", "Památka zesnulých", "Hubert", "Karel", "Miriam",
     "Liběna", "Saskie", "Bohumír", "Bohdan", "Evžen",
     "Martin", "Benedikt", "Tibor", "Sáva", "Leopold",
     "Otmar", "Den boje za svobodu", "Romana", "Alžběta", "Nikola",
     "Albert", "Cecílie", "Klement", "Emílie", "Kateřina",
     "Artur", "Xenie", "René", "Zina", "Ondřej",
     ""},

    // [12] December
    {"",
     "Iva", "Blanka", "Svatoslav", "Barbora", "Jitka",
     "Mikuláš", "Benjamín/Ambrož", "Květoslava", "Vratislav", "Julie",
     "Dana", "Simona", "Lucie", "Lýdie", "Radana/Radan",
     "Albína", "Daniel", "Miloslav", "Ester", "Dagmar",
     "Natálie", "Šimon", "Vlasta", "Adam a Eva", "1.svátek vánoční",
     "Štěpán", "Žaneta", "Bohumila", "Judita", "David",
     "Silvestr"},
};

const char *nameday_get(int month, int day)
{
    if (month < 1 || month > 12 || day < 1 || day > 31) {
        return "";
    }
    const char *name = namedays[month][day];
    return name ? name : "";
}
