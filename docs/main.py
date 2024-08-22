# See: https://mkdocs-macros-plugin.readthedocs.io/en/latest

import os
import subprocess


def define_env(env):
    try:
        proto_tag_raw = subprocess.run(
            ['git', 'describe', '--tags'],
            cwd='../rdf-protos/src/main/protobuf_shared',
            check=True,
            capture_output=True,
        ).stdout.decode().strip()
    except subprocess.CalledProcessError as e:
        print('Failed to call git: ', e.returncode, e.stderr)
        raise e
    

    def proto_tag():
        if proto_tag_raw.count('-') > 1:
            if jvm_version() == 'dev':
                print(f'Warning: proto tag ({proto_tag_raw}) contains more than one hyphen, using dev instead')
                return 'dev'
            else:
                raise ValueError(f'Proto tag ({proto_tag_raw}) contains more than one hyphen, but you are trying to build a tagged release. To fix this, you must update the protobuf_shared submodule to some stable tag.')
        return proto_tag_raw
        

    @env.macro
    def jvm_version():
        tag = os.environ.get('TAG', 'dev')
        if tag == 'dev':
            print('Warning: TAG env var is not set, using dev as default')
            return tag
        elif tag == 'main':
            return 'dev'
        else:
            return tag.replace('v', '')
    
    
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
    def proto_link(page: str):
        version = proto_version()
        return f'https://w3id.org/jelly/{version}/{page}'
    

    @env.macro
    def module_badges(module):
        version = jvm_version()
        if version == 'dev':
            return f'[![Browse jelly-{module} versions](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-{module}/latest.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-{module}) [![Browse latest jelly-{module} API docs](https://javadoc.io/badge2/eu.ostrzyciel.jelly/jelly-{module}_3/javadoc.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-{module}_3)'
        else:
            return f'[![See jelly-{module} {version} module details](https://img.shields.io/badge/jelly--{module}-{version.replace("-", "--")}-green.svg)](https://index.scala-lang.org/jelly-rdf/jelly-jvm/jelly-{module}/{version}) [![Browse jelly-{module} {version} API docs](https://img.shields.io/badge/javadoc-{version.replace("-", "--")}-brightgreen.svg)](https://javadoc.io/doc/eu.ostrzyciel.jelly/jelly-{module}_3/{version})'
    

    def transform_nav_item(item):
        if list(item.values())[0] == 'https://w3id.org/jelly/':
            return {list(item.keys())[0]: proto_link('')}
        return item
    

    env.conf['nav'] = [
        transform_nav_item(item)
        for item in env.conf['nav']
    ]
