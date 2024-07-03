# See: https://mkdocs-macros-plugin.readthedocs.io/en/latest

import os
import subprocess


def define_env(env):

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
        
        tag = subprocess.run(
            ['git', 'describe', '--tags', '--abbrev=0'],
            cwd='../core/src/main/protobuf_shared',
            check=True,
            capture_output=True,
        ).stdout.decode().strip()
        return tag.replace('v', '')

    
    @env.macro
    def proto_link(page: str):
        version = proto_version()
        return f'https://jelly-rdf.github.io/{version}/{page}'
    

    def transform_nav_item(item):
        if list(item.values())[0] == 'https://jelly-rdf.github.io/':
            return {list(item.keys())[0]: proto_link('')}
        return item
    

    env.conf['nav'] = [
        transform_nav_item(item)
        for item in env.conf['nav']
    ]
