"""
GuardianAI — Test du moteur IA sur des fichiers systeme Windows connus benins.

Objectif : mesurer empiriquement le taux de faux positifs sur des binaires
reels et legitimes, et le temps de reponse (exigence RNF-01 : < 3 s).

Note : on appelle 127.0.0.1 et non "localhost". Sous Windows, "localhost"
est resolu d'abord en IPv6 (::1) alors qu'uvicorn n'ecoute qu'en IPv4,
ce qui ajoute environ 2 secondes de latence artificielle par requete.
Une session HTTP reutilisee evite en plus de rouvrir une connexion a chaque appel.

Usage (le serveur uvicorn doit tourner) :
    python test_predict.py
    python test_predict.py --n 50
    python test_predict.py --dossier "C:/Windows/System32" --n 30
"""

import argparse
import glob
import os
import statistics
import time

import requests

API = "http://127.0.0.1:8000/predict"
SESSION = requests.Session()


def tester(chemin: str):
    """Envoie un fichier au moteur et retourne (verdict, score, duree_client, duree_serveur)."""
    with open(chemin, "rb") as f:
        debut = time.perf_counter()
        reponse = SESSION.post(API, files={"file": (os.path.basename(chemin), f)})
        duree = time.perf_counter() - debut

    if reponse.status_code != 200:
        return f"ERREUR {reponse.status_code}", None, duree, None

    data = reponse.json()
    return (data["classification"], data["score_malveillance"], duree,
            data.get("duree_ms"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dossier", default="C:/Windows/System32",
                        help="Dossier contenant les executables a tester.")
    parser.add_argument("--n", type=int, default=25,
                        help="Nombre de fichiers a tester.")
    args = parser.parse_args()

    fichiers = sorted(glob.glob(os.path.join(args.dossier, "*.exe")))[:args.n]
    if not fichiers:
        print(f"Aucun .exe trouve dans {args.dossier}")
        return

    print(f"Test de {len(fichiers)} fichiers systeme (tous supposes benins)\n")
    print(f"{'Fichier':<32} {'Verdict':<13} {'Score':>7} {'Client':>8} {'Serveur':>9}")
    print("-" * 74)

    resultats, durees_client, durees_serveur, signales = [], [], [], []

    for chemin in fichiers:
        nom = os.path.basename(chemin)
        try:
            verdict, score, duree, duree_srv = tester(chemin)
        except requests.exceptions.ConnectionError:
            print("\nServeur injoignable. Lance d'abord :  uvicorn main:app --port 8000")
            return

        resultats.append(verdict)
        durees_client.append(duree)
        if duree_srv is not None:
            durees_serveur.append(duree_srv / 1000)

        aff_score = f"{score:.2f}%" if score is not None else "-"
        aff_srv = f"{duree_srv:.0f}ms" if duree_srv is not None else "-"
        print(f"{nom[:31]:<32} {verdict:<13} {aff_score:>7} {duree:>7.2f}s {aff_srv:>9}")

        if verdict in ("malveillant", "suspect"):
            signales.append((nom, score, verdict))

    # --- Synthese
    analyses = [v for v in resultats if not v.startswith("ERREUR")]
    erreurs = len(resultats) - len(analyses)
    faux_positifs = sum(1 for v in analyses if v == "malveillant")
    suspects = sum(1 for v in analyses if v == "suspect")

    print("\n" + "=" * 74)
    print(f"Analyses reussies        : {len(analyses)} / {len(resultats)}"
          + (f"   ({erreurs} non analysables)" if erreurs else ""))
    if analyses:
        print(f"Faux positifs            : {faux_positifs} "
              f"({faux_positifs / len(analyses) * 100:.1f}%)   [objectif <= 2%]")
        print(f"Classes suspects         : {suspects} "
              f"({suspects / len(analyses) * 100:.1f}%)")
    if durees_serveur:
        srv = sorted(durees_serveur)
        p95 = srv[max(0, int(len(srv) * 0.95) - 1)]
        print(f"\nTemps serveur median     : {statistics.median(srv):.3f}s"
              f"   [objectif < 3s]")
        print(f"Temps serveur 95e perc.  : {p95:.3f}s   [exigence RNF-01]")
        print(f"Temps serveur max        : {max(srv):.3f}s")
    if durees_client:
        print(f"Temps client median      : {statistics.median(durees_client):.3f}s"
              f"   (inclut le reseau et la lecture disque)")

    if signales:
        print("\nFichiers signales (a verifier manuellement) :")
        for nom, score, verdict in signales:
            print(f"  - {nom} : {verdict} ({score:.2f}%)")
    else:
        print("\nAucun fichier systeme signale : aucun faux positif detecte.")


if __name__ == "__main__":
    main()