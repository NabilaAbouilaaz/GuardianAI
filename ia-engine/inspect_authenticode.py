"""
GuardianAI — Diagnostic du groupe de caracteristiques `authenticode`.

Contexte : sur trois binaires Microsoft signes (notepad.exe, calc.exe,
wpnpinst.exe), les valeurs SHAP montrent que le groupe `authenticode` pousse
systematiquement vers un verdict malveillant. Sur des fichiers dont la signature
est valide, c'est contre-intuitif.

Hypothese a tester : signify n'arrive pas a analyser ces signatures, les
caracteristiques restent a leur valeur par defaut, et le modele interprete cet
etat comme celui d'un binaire non signe.

Ce script compare les valeurs extraites sur des fichiers signes et non signes.
Si les deux jeux sont identiques, l'extraction ne distingue pas les deux cas et
l'hypothese est confirmee.

Usage (venv active) :
    python inspect_authenticode.py
    python inspect_authenticode.py --fichiers C:/Windows/System32/cmd.exe autre.exe
"""

import argparse
import os
import traceback

import numpy as np
import thrember

# Bornes relevees par inspect_features.py.
DEBUT, FIN = 2472, 2480

# Binaires systeme Microsoft, tous signes. Le dernier sert de temoin : les
# fichiers de Python installes localement ne portent pas la meme signature.
PAR_DEFAUT = [
    r"C:\Windows\System32\notepad.exe",
    r"C:\Windows\System32\calc.exe",
    r"C:\Windows\System32\cmd.exe",
]

extracteur = thrember.PEFeatureExtractor()


def analyser(chemin: str) -> None:
    nom = os.path.basename(chemin)

    if not os.path.exists(chemin):
        print(f"{nom:<20} introuvable, ignore.")
        return

    with open(chemin, "rb") as f:
        contenu = f.read()

    # 1. Valeurs brutes, avant vectorisation : c'est la qu'on voit si signify a
    #    reellement lu quelque chose ou s'il a echoue en silence.
    try:
        brut = extracteur.raw_features(contenu)
        authenticode = brut.get("authenticode")
    except Exception:
        print(f"{nom:<20} extraction brute impossible :")
        traceback.print_exc()
        return

    # 2. Portion correspondante du vecteur final, celle que voit le modele.
    vecteur = np.asarray(extracteur.feature_vector(contenu), dtype=np.float32)
    tranche = vecteur[DEBUT:FIN]

    print(f"\n{'=' * 70}")
    print(f"{nom}  ({len(contenu)} octets)")
    print(f"{'=' * 70}")
    print(f"Caracteristiques brutes 'authenticode' : {authenticode}")
    print(f"Vecteur [{DEBUT}:{FIN}]                  : {tranche.tolist()}")

    if not np.any(tranche):
        print(">> Toutes les valeurs sont nulles : aucune information de signature "
              "n'a ete extraite pour ce fichier.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fichiers", nargs="*", default=PAR_DEFAUT,
                        help="Fichiers a comparer.")
    args = parser.parse_args()

    print("Diagnostic du groupe 'authenticode' (indices "
          f"{DEBUT} a {FIN - 1}, soit {FIN - DEBUT} dimensions)")

    for chemin in args.fichiers:
        analyser(chemin)

    print(f"\n{'=' * 70}")
    print("Lecture des resultats :")
    print("- Valeurs identiques sur tous les fichiers signes -> l'extraction ne")
    print("  distingue rien, l'hypothese est confirmee.")
    print("- Valeurs nulles partout -> signify n'a rien pu lire.")
    print("- Valeurs differentes et non nulles -> l'extraction fonctionne, la")
    print("  contribution positive vient alors du modele lui-meme.")


if __name__ == "__main__":
    main()
