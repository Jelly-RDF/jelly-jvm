# See: https://mkdocs-macros-plugin.readthedocs.io/en/latest

import os
import subprocess

JELLY_PACKAGE_BASE = 'eu.neverblink.jelly'

# !!! Change this if you want to add or update modules package bases
JELLY_MODULE_TO_PACKAGE_BASE = {
    'jena': 'convert.jena',
    'rdf4j': 'convert.rdf4j',
    'titanium-rdf-api': 'convert.titanium',
    'pekko-stream': 'pekko.stream',
    'pekko-grpc': 'pekko.grpc',
}


def get_package_base_by_module(module: str) -> str:
    if module in JELLY_MODULE_TO_PACKAGE_BASE:
        return f'{JELLY_PACKAGE_BASE}.{JELLY_MODULE_TO_PACKAGE_BASE[module]}'
    else:
        return f'{JELLY_PACKAGE_BASE}.{module}'


def get_package_path_by_module(module: str) -> str:
    return get_package_base_by_module(module).replace('.', '/')


def define_env(env):
    # Override for use in local development
    proto_tag_raw = os.environ.get('PROTO_TAG', None)
    if proto_tag_raw is not None:
        print(f'PROTO_TAG env var is set to {proto_tag_raw}')
    else:
        try:
            proto_tag_raw = subprocess.run(
                ['git', 'describe', '--tags'],
                cwd='../submodules/protobuf',
                check=True,
                capture_output=True,
            ).stdout.decode().strip()
        except subprocess.CalledProcessError as e:
            print('Failed to call git: ', e.returncode, e.stderr)
            raise e
        
    try:
        jvm_tag_raw = subprocess.run(
            ['git', 'describe', '--tags'],
            check=True,
            capture_output=True,
        ).stdout.decode().strip()
    except subprocess.CalledProcessError as e:
        print('Failed to call git: ', e.returncode, e.stderr)
        raise e
    
    tag_env_var = os.environ.get('TAG', 'dev')
    if tag_env_var == 'dev':
        print('Warning: TAG env var is not set, using dev as default')
    

    def proto_tag():
        if proto_tag_raw.count('-') > 1:
            if jvm_version() == 'dev':
                print(f'Warning: proto tag ({proto_tag_raw}) contains more than one hyphen, using dev instead')
                return 'dev'
            else:
                new_tag = '-'.join(proto_tag_raw.split('-')[:2])
                print(f'Warning: proto tag ({proto_tag_raw}) contains more than one hyphen. Using {new_tag} instead. To fix this, you must update the protobuf_shared submodule to some stable tag.')
                return new_tag
        return proto_tag_raw
        

    @env.macro
    def jvm_version():
        if tag_env_var == 'dev':
            return tag_env_var
        elif tag_env_var == 'main':
            return 'dev'
        else:
            return tag_env_var.replace('v', '')
        
    
    @env.macro
    def jvm_package_version():
        """Returns the current JVM package version, as published to Maven."""
        v = jvm_version()
        if v == 'dev':
            return jvm_tag_raw.split('-')[0].replace('v', '')
        else:
            return v

    
    @env.macro
    def jvm_package_version_minor():
        """Returns the current MINOR JVM package version, as published to Maven."""
        v = jvm_package_version()
        return '.'.join(v.split('.')[:2]) + ".x"


    @env.macro
    def git_tag():
        return os.environ.get('TAG', 'main')
        
    
    @env.macro
    def git_link(file: str):
        tag = git_tag()
        return f'https://github.com/Jelly-RDF/jelly-jvm/blob/{tag}/{file}'
    

    @env.macro
    def proto_version():
        if jvm_version() == 'dev':
            return 'dev'
        tag = proto_tag()
        if '-' in tag:
            print('Warning: proto tag contains a hyphen, using dev instead of ' + tag)
            return 'dev'
        return tag.replace('v', '')

    
    @env.macro
    def proto_link(page: str = ''):
        version = proto_version()
        return f'https://w3id.org/jelly/{version}/{page}'

    def _module_badges(module, is_scala: bool = False):
        version = jvm_version()
        if version == 'dev':
            version = jvm_package_version()

        scala_suffix = ''
        if is_scala:
            scala_suffix = '_3'

        if version == 'dev':
            return f'[![Maven Central Version](https://img.shields.io/badge/maven--central-{version.replace("-", "--")}-green.svg)](https://central.sonatype.com/artifact/eu.neverblink.jelly/jelly-{module}{scala_suffix}/{version}) [![Browse latest jelly-{module} API docs](https://javadoc.io/badge2/eu.neverblink.jelly/jelly-{module}{scala_suffix}/javadoc.svg)](https://javadoc.io/doc/eu.neverblink.jelly/jelly-{module}{scala_suffix})'
        else:
            return f'[![Maven Central Version](https://img.shields.io/badge/maven--central-{version.replace("-", "--")}-green.svg)](https://central.sonatype.com/artifact/eu.neverblink.jelly/jelly-{module}{scala_suffix}/{version}) [![Browse jelly-{module} {version} API docs](https://img.shields.io/badge/javadoc-{version.replace("-", "--")}-brightgreen.svg)](https://javadoc.io/doc/eu.neverblink.jelly/jelly-{module}{scala_suffix}/{version})'

    @env.macro
    def java_module_badges(module):
        return _module_badges(module, is_scala=False)

    @env.macro
    def scala_module_badges(module):
        return _module_badges(module, is_scala=True)

    def transform_nav_item(item):
        if list(item.values())[0] == 'https://w3id.org/jelly/':
            return {list(item.keys())[0]: proto_link('')}
        return item
    

    @env.macro
    def code_example(file_name):
        prefix = 'examples/src/main/scala/eu/neverblink/jelly/examples/'
        if file_name.endswith('.java'):
            dir_name = 'examples_java'
            lang = 'java'
        else: 
            dir_name = 'examples'
            lang = 'scala'
        with open(f'docs/{dir_name}/{file_name}', 'r') as f:
            code = f.read()
        return f"""
??? example "Example: {file_name} (click to expand)"

    **[:octicons-code-24: Source code on GitHub]({git_link(prefix + file_name)})**

    ```{lang} title="{file_name}" linenums="1"
    {code.replace('\n', '\n    ')}
    ```
"""

    def _javadoc_link(module: str, clazz: str, is_scala: bool = False):
        version = jvm_package_version()
        clazz = f'{get_package_base_by_module(module)}/{clazz}'
        clazz = clazz.replace('.', '/')
        if version == 'dev':
            version = 'latest'

        scala_suffix = ''
        if is_scala:
            scala_suffix = '_3'

        return f'https://javadoc.io/static/eu.neverblink.jelly/jelly-{module}{scala_suffix}/{version}/{clazz}.html'

    def _javadoc_link_package(module: str, package_name: str, is_scala: bool = False):
        version = jvm_package_version()
        package_name = f'{get_package_base_by_module(module)}/{package_name}'
        package_name = package_name.replace('.', '/')
        if version == 'dev':
            version = 'latest'

        scala_suffix = ''
        if is_scala:
            scala_suffix = '_3'

        return f'https://javadoc.io/static/eu.neverblink.jelly/jelly-{module}{scala_suffix}/{version}/{package_name}/package-summary.html'

    @env.macro
    def javadoc_link(module: str, clazz: str):
        return _javadoc_link(module, clazz, is_scala=False)

    @env.macro
    def scaladoc_link(module: str, clazz: str):
        return _javadoc_link(module, clazz, is_scala=True)

    @env.macro
    def javadoc_package_link(module: str, package_name: str):
        return _javadoc_link_package(module, package_name, is_scala=False)

    @env.macro
    def scaladoc_package_link(module: str, package_name: str):
        return _javadoc_link_package(module, package_name, is_scala=True)

    @env.macro
    def javadoc_link_pretty(module: str, clazz: str, fun: str = ''):
        name = f'{get_package_base_by_module(module)}.{clazz}'
        if fun:
            name += f'.{fun}'
        return f"[`{name.replace('$', '')}` :material-api:]({_javadoc_link(module, clazz, is_scala=False)})"

    @env.macro
    def scaladoc_link_pretty(module: str, clazz: str, fun: str = ''):
        name = f'{get_package_base_by_module(module)}.{clazz}'
        if fun:
            name += f'.{fun}'
        return f"[`{name.replace('$', '')}` :material-api:]({_javadoc_link(module, clazz, is_scala=True)})"

    @env.macro
    def javadoc_package_link_pretty(module: str, package_name: str):
        name = f'{get_package_base_by_module(module)}.{package_name}'
        return f"[`{name}` :material-api:]({_javadoc_link_package(module, package_name)})"

    @env.macro
    def scaladoc_package_link_pretty(module: str, package_name: str):
        name = f'{get_package_base_by_module(module)}.{package_name}'
        return f"[`{name}` :material-api:]({_javadoc_link_package(module, package_name, is_scala=True)})"

    env.conf['nav'] = [
        transform_nav_item(item)
        for item in env.conf['nav']
    ]
