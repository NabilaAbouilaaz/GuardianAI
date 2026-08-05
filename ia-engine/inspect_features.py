"""
GuardianAI — Releve de la structure du vecteur de caracteristiques.

Le vecteur produit par thrember est la concatenation de plusieurs extracteurs,
chacun couvrant un aspect du fichier (en-tetes, sections, imports...). Pour
agreger les valeurs SHAP par groupe, il faut connaitre les bornes d'indices de
chacun. Ces bornes sont lues ici depuis la bibliotheque installee plutot que
supposees : une erreur d'un seul indice attribuerait silencieusement une
contribution au mauvais groupe.

Le resultat est a reporter dans main.py et a documenter dans NOTES.md.

Usage (venv active) :
    python inspect_features.py
"""

import thrember

extracteur = thrember.PEFeatureExtractor()

print(f"Dimension totale du vecteur : {extracteur.dim}\n")
print(f"{'Groupe':<28} {'Debut':>7} {'Fin':>7} {'Taille':>8}")
print("-" * 54)

debut = 0
bornes = {}

for sous_extracteur in extracteur.features:
    taille = sous_extracteur.dim
    fin = debut + taille - 1
    bornes[sous_extracteur.name] = (debut, debut + taille)
    print(f"{sous_extracteur.name:<28} {debut:>7} {fin:>7} {taille:>8}")
    debut += taille

print("-" * 54)
print(f"{'Somme des groupes':<28} {'':>7} {'':>7} {debut:>8}")

if debut != extracteur.dim:
    print("\nATTENTION : la somme des groupes ne correspond pas a la dimension "
          "annoncee. Ne pas utiliser ces bornes en l'etat.")
else:
    print("\nCoherent. Bornes utilisables.\n")
    print("A recopier dans main.py :\n")
    print("GROUPES = {")
    for nom, (i, j) in bornes.items():
        print(f'    "{nom}": ({i}, {j}),')
    print("}")
