"""Genera R.kt (ids estilo Android) a partir de strings.xml, drawables y fuentes de la app Android,
para que el código portado siga usando R.string.x / R.drawable.x / R.font.x sin cambios."""
import os, re, sys, xml.etree.ElementTree as ET
res, cr, out = sys.argv[1], sys.argv[2], sys.argv[3]

def unescape(s):
    s = s.strip() if not (s.startswith('"') and s.endswith('"')) else s[1:-1]
    r = []; i = 0
    while i < len(s):
        c = s[i]
        if c == '\\' and i + 1 < len(s):
            n = s[i+1]; r.append({'n': '\n', 't': '\t', "'": "'", '"': '"', '\\': '\\', '@': '@', '?': '?'}.get(n, n)); i += 2; continue
        r.append(c); i += 1
    return ''.join(r)

def kstr(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('\n', '\\n').replace('\t', '\\t') + '"'

root = ET.parse(os.path.join(res, 'values/strings.xml')).getroot()
strings = [(e.get('name'), unescape(''.join(e.itertext()))) for e in root if e.tag == 'string']
draw = sorted(os.path.splitext(f)[0] for f in os.listdir(os.path.join(cr, 'drawable')))
fonts = sorted(os.path.splitext(f)[0] for f in os.listdir(os.path.join(cr, 'font')))
L = ['// Archivo generado por tools/gen_r.py -- no editar a mano.', 'package com.readriffle.app', '',
     'import com.readriffle.web.res.*', 'import org.jetbrains.compose.resources.DrawableResource', 'import org.jetbrains.compose.resources.FontResource', '',
     'object R {', '    object string {']
L += [f'        const val {n} = {i}' for i, (n, _) in enumerate(strings)]
L += ['    }', '    object drawable {']
L += [f'        const val {n} = {i}' for i, n in enumerate(draw)]
L += ['    }', '    object font {']
L += [f'        const val {n} = {i}' for i, n in enumerate(fonts)]
L += ['    }', '}', '', 'internal val STRING_TABLE: Array<String> = arrayOf(']
L += [f'    {kstr(v)},' for _, v in strings]
L += [')', '', 'internal val DRAWABLE_TABLE: Array<DrawableResource> by lazy { arrayOf(']
L += [f'    Res.drawable.{n},' for n in draw]
L += [') }', '', 'internal val FONT_TABLE: Array<FontResource> by lazy { arrayOf(']
L += [f'    Res.font.{n},' for n in fonts]
L += [') }', '', 'internal val FONT_FILES: Array<String> = arrayOf(']
L += [f'    "font/{n}.ttf",' for n in fonts]
L += [')', '']
open(out, 'w').write('\n'.join(L))
print(len(strings), len(draw), len(fonts))
