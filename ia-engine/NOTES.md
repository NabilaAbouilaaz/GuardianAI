# Notes techniques — ia-engine

## Patch requis : ember + lief (compatibilité de version)

Le package `ember` (installé via `pip install git+https://github.com/elastic/ember.git`)
a été écrit pour lief ~0.9.0-0.11.x. La version de lief installée dans ce projet est
plus récente (1.0.0+), dont l'API a changé. Sans ce patch, `/predict` plante.

**Fichier à corriger après chaque réinstallation de `ember`** :
`venv/Lib/site-packages/ember/features.py`

Trois corrections à appliquer dans ce fichier (voir `PEFeatureExtractor.raw_features`
et la classe `SectionInfo`) :

1. **`lief_errors`** (méthode `PEFeatureExtractor.raw_features`) :
```python
lief_errors = (lief.lief_errors.corrupted, lief.lief_errors.file_format_error, lief.lief_errors.file_error, lief.lief_errors.parsing_error, lief.lief_errors.read_out_of_bound, RuntimeError)
```

2. **`np.int` déprécié** (classe `ByteEntropyHistogram`, méthode `raw_features`) :
```python
output = np.zeros((16, 16), dtype=np.int64)
```

3. **`lief.not_found`** et **`lief.PE.SECTION_CHARACTERISTICS`** (classe `SectionInfo`, méthode `raw_features`) :
```python
except Exception:  # au lieu de except lief.not_found:
...
if lief.PE.Section.CHARACTERISTICS.MEM_EXECUTE in s.characteristics_lists:
```

4. **FeatureHasher avec chaîne simple** (classe `SectionInfo`, méthode `process_raw_features`) :
```python
entry_name_hashed = FeatureHasher(50, input_type="string").transform([[raw_obj['entry']]]).toarray()[0]
```

## TODO / amélioration future

Idéalement, forker `elastic/ember` sur notre propre repo GitHub avec ces correctifs
appliqués, et installer depuis notre fork plutôt que de patcher manuellement à chaque
installation. À faire si le temps le permet.