# Recoloca o logo e a arte da tela inicial no index.html,
# copiando-os do seu arquivo antigo (original.html), se ele existir.
import re, os, sys
if not os.path.exists('original.html'):
    print('original.html nao encontrado: seguindo sem as imagens da tela inicial.'); sys.exit(0)
o = open('original.html', encoding='utf-8').read()
n = open('index.html', encoding='utf-8').read()
art = re.search(r'url\((data:image/[a-z]+;base64,[A-Za-z0-9+/=]+)\)', o)
logo = re.search(r'<img class="hm-logo"[^>]*src="(data:image/[a-z]+;base64,[A-Za-z0-9+/=]+)"', o)
if art:
    n = n.replace('--hm-art: none;', '--hm-art: url(' + art.group(1) + ');', 1); print('arte da tela inicial: OK')
else: print('arte nao encontrada')
if logo:
    ph = 'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='
    n = n.replace(ph, logo.group(1), 1); print('logo: OK')
else: print('logo nao encontrado')
open('index.html', 'w', encoding='utf-8').write(n)
