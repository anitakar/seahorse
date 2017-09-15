'use strict';

require('./attribute-code-snippet-type.ctrl.js');
import tpl from './attribute-code-snippet-type.html';

/* @ngInject */
function AttributeCodeSnippetType() {
  return {
    restrict: 'E',
    templateUrl: tpl,
    replace: false,
    scope: {
      value: '=',
      language: '='
    },
    bindToController: true,
    controller: 'AttributeCodeSnippetTypeCtrl',
    controllerAs: 'acstCtrl'
  };
}

angular
  .module('deepsense.attributes-panel')
  .directive('attributeCodeSnippetType', AttributeCodeSnippetType);

