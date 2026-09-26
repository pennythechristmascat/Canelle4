package fr.canelle.compagnon

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

/** Health Connect exige une page qui explique à quoi servent les pas lus par l'application. */
class StepsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle(Lang.t("Pourquoi Canelle AI lit tes pas"))
            .setMessage(Lang.t(
                "Canelle AI lit seulement ton nombre de pas, pour l'afficher dans l'onglet « Pas », " +
                    "suivre ton objectif du jour et te donner des pièces. Rien n'est envoyé sur internet : " +
                    "tout reste sur ton téléphone. Tu peux retirer l'autorisation à tout moment dans Health Connect."
            ))
            .setPositiveButton(Lang.t("J'ai compris")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
