functor

import
   OzmaRuntime('NewObject':NewObject) at 'x-ozma://system/OzmaRuntime.ozf'
   `functor:java.lang.Object`('type:java.lang.Object':`type:java.lang.Object`
                              'class:java.lang.Object':`class:java.lang.Object`) at 'x-ozma://root/java/lang/Object.ozf'
   `functor:java.lang.Class`('type:java.lang.Class':`type:java.lang.Class`
                             'class:java.lang.Class':`class:java.lang.Class`) at 'x-ozma://root/java/lang/Class.ozf'

export
   'type:scala.runtime.VolatileObjectRef':`type:scala.runtime.VolatileObjectRef`
   'class:scala.runtime.VolatileObjectRef':`class:scala.runtime.VolatileObjectRef`

define

   class `type:scala.runtime.VolatileObjectRef` from `type:java.lang.Object`
      attr
         ' elem'

      meth '<init>#897249255'(Value $)
         ' elem' := Value
         `type:java.lang.Object`, '<init>#1063877011'($)
      end

      meth 'toString#1195259493'($)
         {@' elem' toString($)}
      end
   end

   `class:scala.runtime.VolatileObjectRef` = {ByNeed fun {$}
                                                        {NewObject `type:java.lang.Class`
                                                         `class:java.lang.Class`
                                                         '<init>'("scala.runtime.VolatileObjectRef"
                                                                  `class:java.lang.Object`
                                                                  nil
                                                                  [`class:java.lang.Object`]
                                                                  _)}
                                                     end}

end
