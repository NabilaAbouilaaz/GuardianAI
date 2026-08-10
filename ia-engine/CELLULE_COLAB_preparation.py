# =====================================================================
# GuardianAI — Préparation de la VM Colab (à exécuter AVANT la recalibration)
#
# La VM Colab est recyclée après quelques heures d'inactivité : le dossier
# /content est vidé, les vecteurs .dat disparaissent. Google Drive, lui,
# persiste — le modèle entraîné y est toujours.
#
# Cette cellule reconstruit uniquement les données. Elle NE réentraîne PAS :
# l'entraînement coûte 20 minutes et n'apporterait rien, le modèle existant
# étant celui qu'on veut évaluer.
#
# Durée attendue : 20 à 40 minutes selon le débit.
# =====================================================================

import os
import time

t0 = time.time()

# --- 1. Drive (le modèle y est stocké)
from google.colab import drive

if not os.path.exists("/content/drive/MyDrive"):
    drive.mount("/content/drive")

OUT = "/content/drive/MyDrive/GuardianAI/models"
assert os.path.exists(f"{OUT}/lightgbm_ember2024_v2.joblib"), (
    "Modele introuvable sur Drive. Verifier le chemin avant de continuer."
)
print("Modele present sur Drive — aucun reentrainement necessaire.")

# --- 2. Réinstallation des dépendances (la VM est neuve)
print("\nInstallation des dependances...")
os.system("git clone -q https://github.com/FutureComputing4AI/EMBER2024.git")
os.system("pip install -q ./EMBER2024")
os.system('pip install -q "signify==0.7.1" lightgbm scikit-learn')

import thrember

print(f"[{(time.time() - t0) / 60:.1f} min] thrember OK")

# --- 3. Téléchargement du jeu de données
# Mêmes sous-ensembles que lors de l'entraînement : les vecteurs doivent être
# strictement identiques, sinon l'évaluation ne porterait pas sur les mêmes
# données que celles ayant produit le modèle.
DATA = "/content/ember2024"
os.makedirs(DATA, exist_ok=True)

thrember.download_dataset(DATA, file_type="Dot_Net")
thrember.download_dataset(DATA, file_type="Win64")
thrember.download_dataset(DATA, split="challenge")
print(f"[{(time.time() - t0) / 60:.1f} min] telechargement termine")

# --- 4. Vectorisation
thrember.create_vectorized_features(DATA)
print(f"[{(time.time() - t0) / 60:.1f} min] vectorisation terminee")

os.system(f"ls -lh {DATA}/*.dat")
print(f"\nTOTAL : {(time.time() - t0) / 60:.1f} min")
print("Executer maintenant la cellule de recalibration.")
